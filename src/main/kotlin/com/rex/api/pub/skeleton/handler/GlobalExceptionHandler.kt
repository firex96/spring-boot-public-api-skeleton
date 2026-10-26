package com.rex.api.pub.skeleton.handler

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.exception.CodedException
import com.rex.api.pub.skeleton.model.ErrorResponseModel
import com.rex.api.pub.skeleton.util.logger
import jakarta.validation.ConstraintViolationException
import org.springframework.boot.json.JsonParseException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpClientErrorException.BadRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.core.JacksonException
import java.io.IOException

/**
 * 오류 응답을 [ErrorResponseModel] 로 만든다.
 *
 * - HTTP 상태는 언제나 응답에 실은 [ErrorCode] 의 status 다 — 본문 `status` 와 HTTP 상태가 갈리지 않게 상태를 정하는 곳을 [respond] 하나로 둔다.
 * - `reason` 에는 사용자에게 보여도 되는 문장만 싣는다. 업무 예외 4xx 는 예외 메시지, 그 밖은 [ErrorReason] 의 고정 문장(검증 실패는 필드별 검증 메시지)이다.
 *   원래 예외 메시지는 [respond] 가 로그에만 남긴다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    // ================================
    // Custom Exception Handlers
    // ================================

    /** 업무 예외 — `CodedException` 하위 타입 전부. 상태·구분 코드는 예외의 `errorCode` 가 정한다. */
    @ExceptionHandler(CodedException::class)
    protected fun handleCodedException(ex: CodedException): ResponseEntity<ErrorResponseModel> = respond(ex.errorCode, ex.message ?: ex.errorCode.message, ex)

    /**
     * 외부 API 에 연결하지 못한 경우(연결 거부·타임아웃·DNS) — 우리 요청의 문제가 아니라 상대 가용성 문제라 503 으로 돌려준다.
     * 상대가 응답했는데 처리에 실패한 경우(역직렬화 실패 등)는 여기 오지 않고 500 으로 남는다.
     */
    @ExceptionHandler(WebClientRequestException::class)
    protected fun handleWebClientRequestException(ex: WebClientRequestException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.SERVICE_UNAVAILABLE_ERROR, ErrorReason.EXTERNAL_SERVICE_ERROR, ex)

    // ================================
    // Auth Exception Handlers
    // ================================

    /**
     * 메서드 레벨 `@PreAuthorize` 거부 — Spring Security 가 던지는 `AuthorizationDeniedException`(`AccessDeniedException` 하위)이다.
     * 이 핸들러가 없으면 맨 아래 `Exception` 캐치올이 잡아 **403 이어야 할 응답이 500** 으로 나간다(필터 체인 거부만 403 이었다).
     *
     * 이 어드바이스가 예외를 먼저 잡으므로 `ExceptionTranslationFilter` 의 익명 → 엔트리포인트 분기가 돌지 않는다.
     * 그래서 인증되지 않은 요청의 거부는 여기서 401 로 바꾼다 — 필터 체인 엔트리포인트와 같은 응답(코드·문장)이다.
     */
    @ExceptionHandler(AccessDeniedException::class)
    protected fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponseModel> {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || authentication is AnonymousAuthenticationToken) {
            return respond(ErrorCode.UNAUTHORIZED_ERROR, ErrorReason.UNAUTHORIZED, ex)
        }
        return respond(ErrorCode.FORBIDDEN_ERROR, ErrorReason.FORBIDDEN, ex)
    }

    // ================================
    // Validation Exception Handlers
    // ================================

    // 메서드 파라미터 검증 실패 — 파라미터 이름과 검증 메시지(애너테이션에 적은 사용자 문장)를 잇는다
    @ExceptionHandler(ConstraintViolationException::class)
    protected fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorResponseModel> {
        val reason =
            ex.constraintViolations
                .map { violation -> "${violation.propertyPath.last().name}: ${violation.message}" }
                .distinct()
                .sorted() // 위반 목록은 순서가 없는 집합이라 정렬해 응답 문구를 결정적으로 만든다
                .joinToString(", ")
        return respond(ErrorCode.NOT_VALID_ERROR, reason.ifBlank { ErrorReason.INVALID_VALUE }, ex)
    }

    // API 호출 시 '객체' 혹은 '파라미터' 데이터 값이 유효하지 않은 경우
    // @JsonProperty 어노테이션이 있는 경우 해당 이름으로 필드명 변환
    @ExceptionHandler(MethodArgumentNotValidException::class)
    protected fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponseModel> {
        val targetClass: Class<*>? = ex.bindingResult.target?.javaClass
        val fieldToJsonKeyMap = targetClass?.let { getJsonPropertyMap(it) } ?: emptyMap()

        val errorMessage =
            ex.bindingResult.fieldErrors
                .map { error ->
                    val field = fieldToJsonKeyMap.getOrDefault(error.field, error.field)
                    // 바인딩 실패(타입 변환 실패)의 기본 메시지는 Spring 변환 예외 문구라 타입·클래스 이름과 입력값이 들어 있다 — 고정 문장으로 바꾼다
                    if (error.isBindingFailure) {
                        ErrorReason.invalidFormat(field)
                    } else {
                        "$field: ${error.defaultMessage ?: "유효하지 않은 값입니다"}"
                    }
                }.distinct()
                .joinToString(", ")

        return respond(ErrorCode.NOT_VALID_ERROR, errorMessage.ifBlank { ErrorReason.INVALID_VALUE }, ex)
    }

    // 경로 변수·파라미터의 타입이 맞지 않는 경우
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    protected fun handleMethodArgumentTypeMismatchException(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.NOT_VALID_ERROR, ErrorReason.invalidFormat(ex.name), ex)

    // 필수 헤더가 없는 경우
    @ExceptionHandler(MissingRequestHeaderException::class)
    protected fun handleMissingRequestHeaderException(ex: MissingRequestHeaderException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.NOT_VALID_HEADER_ERROR, ErrorReason.missingHeader(ex.headerName), ex)

    // 요청 Body 를 읽지 못한 경우.
    //
    // 「본문 누락」과 「본문 형식 오류」를 구분한다. 둘 다 400 이지만 소비자가 재시도 여부를
    // 판단하려면 달라야 한다 — 누락은 본문을 안 보낸 것이고, 형식 오류는 보냈는데 깨진 것이다.
    // 원인이 Jackson 파싱 실패면 JACKSON_PROCESS_ERROR, 그 외(본문 부재 등)는 REQUEST_BODY_MISSING_ERROR.
    @ExceptionHandler(HttpMessageNotReadableException::class)
    protected fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponseModel> =
        if (hasJacksonCause(ex)) {
            respond(ErrorCode.JACKSON_PROCESS_ERROR, ErrorReason.MALFORMED_BODY, ex)
        } else {
            respond(ErrorCode.REQUEST_BODY_MISSING_ERROR, ErrorReason.MISSING_BODY, ex)
        }

    // Spring MVC 는 본문 파싱 실패를 HttpMessageNotReadableException 으로 감싸므로
    // 원인 사슬을 따라가야 Jackson 예외인지 알 수 있다.
    private fun hasJacksonCause(ex: Throwable): Boolean = generateSequence(ex.cause) { it.cause }.any { it is JacksonException }

    // 클라이언트에서 request로 '파라미터로' 데이터가 넘어오지 않았을 경우
    @ExceptionHandler(MissingServletRequestParameterException::class)
    protected fun handleMissingServletRequestParameterException(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.MISSING_REQUEST_PARAMETER_ERROR, ErrorReason.missingParameter(ex.parameterName), ex)

    // ================================
    // HTTP Error Handlers
    // ================================

    // 잘못된 서버 요청일 경우 발생한 경우
    @ExceptionHandler(BadRequest::class)
    protected fun handleBadRequestException(ex: HttpClientErrorException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.BAD_REQUEST_ERROR, ErrorReason.BAD_REQUEST, ex)

    // 잘못된 주소로 요청 한 경우
    @ExceptionHandler(NoHandlerFoundException::class, NoResourceFoundException::class)
    protected fun handleNotFoundPath(ex: Exception): ResponseEntity<ErrorResponseModel> = respond(ErrorCode.NOT_FOUND_ERROR, ErrorReason.PATH_NOT_FOUND, ex)

    // ================================
    // General Exception Handlers
    // ================================

    // NULL 값이 발생한 경우
    @ExceptionHandler(NullPointerException::class)
    protected fun handleNullPointerException(ex: NullPointerException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.NULL_POINT_ERROR, ErrorReason.SERVER_ERROR, ex)

    @ExceptionHandler(NoSuchElementException::class)
    protected fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.NOT_FOUND_ERROR, ErrorReason.TARGET_NOT_FOUND, ex)

    @ExceptionHandler(IOException::class)
    protected fun handleIOException(ex: IOException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.IO_ERROR, ErrorReason.SERVER_ERROR, ex)

    @ExceptionHandler(JsonParseException::class)
    protected fun handleJsonParseException(ex: JsonParseException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.JSON_PARSE_ERROR, ErrorReason.MALFORMED_BODY, ex)

    // tools.jackson.core 내에 Exception 발생하는 경우 — 본문 누락이 아니라 형식 오류다
    @ExceptionHandler(JacksonException::class)
    protected fun handleJacksonException(ex: JacksonException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.JACKSON_PROCESS_ERROR, ErrorReason.MALFORMED_BODY, ex)

    @ExceptionHandler(IllegalArgumentException::class)
    protected fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.NOT_VALID_ERROR, ErrorReason.INVALID_VALUE, ex)

    @ExceptionHandler(Exception::class)
    protected fun handleAllExceptions(ex: Exception): ResponseEntity<ErrorResponseModel> =
        respond(ErrorCode.INTERNAL_SERVER_ERROR, ErrorReason.SERVER_ERROR, ex)

    // ================================
    // Helper Methods
    // ================================

    /**
     * 응답 상태를 [errorCode] 의 status 로 정하고 원래 예외를 로그에 남긴다 — 4xx 는 `warn`(메시지), 5xx 는 `error`(스택).
     * 5xx 는 [reason] 과 무관하게 [ErrorReason.forServerError] 의 고정 문장을 싣는다 — 어느 핸들러가 무엇을 넘겨도 내부 상세가 나가지 않는다.
     * 이 클래스에서 HTTP 상태·응답 문구를 확정하고 로그를 남기는 유일한 곳이다.
     */
    private fun respond(
        errorCode: ErrorCode,
        reason: String,
        ex: Exception,
    ): ResponseEntity<ErrorResponseModel> {
        val serverError = errorCode.status >= 500
        if (serverError) {
            logger.error("{} {}", errorCode.divisionCode, ex.javaClass.simpleName, ex)
        } else {
            logger.warn("{} {}: {}", errorCode.divisionCode, ex.javaClass.simpleName, ex.message)
        }
        val body = ErrorResponseModel.of(errorCode, if (serverError) ErrorReason.forServerError(errorCode) else reason)
        return ResponseEntity(body, HttpStatus.valueOf(errorCode.status))
    }

    /**
     * 클래스의 필드에서 @JsonProperty 어노테이션의 값을 추출하여 필드명과 매핑
     * @param clazz 대상 클래스
     * @return 필드명 -> JsonProperty 값 매핑
     */
    private fun getJsonPropertyMap(clazz: Class<*>): Map<String, String> =
        clazz.declaredFields.associate { field ->
            val jsonProperty = field.getAnnotation(JsonProperty::class.java)
            field.name to (jsonProperty?.value ?: field.name)
        }

    companion object {
        private val logger = logger()
    }
}