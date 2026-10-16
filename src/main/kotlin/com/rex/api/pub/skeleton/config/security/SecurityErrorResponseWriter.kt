package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.model.ErrorResponseModel
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * 필터 체인 단계(엔트리포인트·거부 핸들러)의 401·403 본문을 [ErrorResponseModel] 로 쓴다.
 *
 * 컨트롤러 이후 단계는 `GlobalExceptionHandler` 가 같은 모델을 쓴다 — 두 단계의 본문이 갈리면
 * 같은 403 이 어느 계층에서 거부됐느냐에 따라 형식이 달라진다(OAuth2 프로토콜 엔드포인트만 RFC 6749 형식).
 */
@Component
class SecurityErrorResponseWriter(
    private val jsonMapper: JsonMapper,
) {
    fun write(response: HttpServletResponse, errorCode: ErrorCode, reason: String?) {
        response.status = errorCode.status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.outputStream, ErrorResponseModel.of(errorCode, reason))
    }
}