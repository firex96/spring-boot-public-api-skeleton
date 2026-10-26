package com.rex.api.pub.skeleton.handler

import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.exception.ApiCallException
import com.rex.api.pub.skeleton.exception.ClientBadRequestException
import com.rex.api.pub.skeleton.exception.CodedException
import com.rex.api.pub.skeleton.exception.InvalidRequestParameterException
import com.rex.api.pub.skeleton.exception.ItemNotFoundException
import com.rex.api.pub.skeleton.exception.auth.AuthorizationException
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
import jakarta.validation.Validator
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientRequestException
import tools.jackson.core.exc.StreamReadException
import java.io.IOException
import java.net.ConnectException
import java.net.URI

/** `GlobalExceptionHandlerTest`·`ErrorResponseContractIntegrationTest` 가 쓰는 예외 발생용 컨트롤러. */
@RestController
@RequestMapping("/test/exception")
class TestExceptionController(
    private val validator: Validator,
) {
    @GetMapping("/item-not-found")
    fun throwItemNotFoundException(): ResponseEntity<Any> = throw ItemNotFoundException("User with id 123 not found")

    @GetMapping("/client-bad-request")
    fun throwClientBadRequestException(): ResponseEntity<Any> = throw ClientBadRequestException("Invalid client request")

    @GetMapping("/invalid-parameter")
    fun throwInvalidRequestParameterException(): ResponseEntity<Any> = throw InvalidRequestParameterException("Parameter 'id' must be positive")

    @GetMapping("/api-call")
    fun throwApiCallException(): ResponseEntity<Any> = throw ApiCallException("External service is unavailable", ErrorCode.SERVICE_UNAVAILABLE_ERROR)

    @GetMapping("/api-call-default")
    fun throwDefaultApiCallException(): ResponseEntity<Any> = throw ApiCallException("Upstream responded with an error")

    @GetMapping("/web-client-request")
    fun throwWebClientRequestException(): ResponseEntity<Any> =
        throw WebClientRequestException(ConnectException("Connection refused"), HttpMethod.GET, URI.create("http://upstream.invalid"), HttpHeaders())

    @GetMapping("/coded")
    fun throwCodedException(): ResponseEntity<Any> = throw CodedException(ErrorCode.CONFLICT_ERROR, "이미 처리된 요청입니다")

    @GetMapping("/null-pointer")
    fun throwNullPointerException(): ResponseEntity<Any> = throw NullPointerException("value was null")

    @GetMapping("/io")
    fun throwIOException(): ResponseEntity<Any> = throw IOException("disk read failed")

    @PostMapping("/validate")
    fun validateRequest(
        @Valid @RequestBody request: TestValidationRequest,
    ): ResponseEntity<Any> = ResponseEntity.ok(request)

    @PostMapping("/strict")
    fun strictRequest(
        @RequestBody request: TestStrictRequest,
    ): ResponseEntity<Any> = ResponseEntity.ok(request)

    @GetMapping("/model-attribute")
    fun modelAttribute(
        @Valid @ModelAttribute request: TestFormRequest,
    ): ResponseEntity<Any> = ResponseEntity.ok(request)

    @GetMapping("/missing-header")
    fun missingHeader(
        @RequestHeader("X-Test-Header") value: String,
    ): ResponseEntity<Any> = ResponseEntity.ok(value)

    @GetMapping("/missing-param")
    fun missingParam(
        @RequestParam size: Int,
    ): ResponseEntity<Any> = ResponseEntity.ok(size)

    @GetMapping("/type-mismatch/{id}")
    fun typeMismatch(
        @PathVariable id: Long,
    ): ResponseEntity<Any> = ResponseEntity.ok(id)

    @GetMapping("/constraint")
    fun throwConstraintViolationException(): ResponseEntity<Any> =
        throw ConstraintViolationException(validator.validate(TestValidationRequest(userName = "k", userEmail = "kim@example.com")))

    @GetMapping("/jackson")
    fun throwJacksonException(): ResponseEntity<Any> = throw StreamReadException("internal parser detail")

    @GetMapping("/illegal-argument")
    fun throwIllegalArgumentException(): ResponseEntity<Any> = throw IllegalArgumentException("내부 상세 Invalid argument provided")

    @GetMapping("/no-such-element")
    fun throwNoSuchElementException(): ResponseEntity<Any> = throw NoSuchElementException("내부 상세 Element not found")

    @GetMapping("/generic")
    fun throwGenericException(): ResponseEntity<Any> = throw RuntimeException("내부 상세 com.rex.Secret")

    @GetMapping("/authorization")
    fun throwAuthorizationException(): ResponseEntity<Any> = throw AuthorizationException("그룹을 관리할 권한이 없습니다")

    @GetMapping("/access-denied")
    fun throwAccessDeniedException(): ResponseEntity<Any> = throw AccessDeniedException("Access Denied")
}