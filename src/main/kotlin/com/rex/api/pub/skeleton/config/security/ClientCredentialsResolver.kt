package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * `/oauth2/token`·`introspect`·`revoke` 의 클라이언트 자격을 요청에서 꺼낸다 — discovery 가 광고하는 두 방식만.
 *
 * - `client_secret_basic`: `Authorization: Basic base64(urlencode(id):urlencode(secret))` (RFC 6749 §2.3.1)
 * - `client_secret_post`: 폼 `client_id`·`client_secret`
 *
 * 한 요청에 두 방식을 함께 쓰면 `invalid_request` 다(RFC 6749 §2.3 — 하나만 쓴다). 형식이 깨진 Basic 은 `invalid_client`.
 */
@Component
class ClientCredentialsResolver {
    fun resolve(request: HttpServletRequest, formClientId: String?, formClientSecret: String?): ClientCredentials {
        val header = request.getHeader(AUTHORIZATION_HEADER)
        if (header == null || !header.startsWith(BASIC_PREFIX, ignoreCase = true)) {
            return ClientCredentials(clientId = formClientId, clientSecret = formClientSecret)
        }
        if (!formClientSecret.isNullOrEmpty()) {
            throw OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_REQUEST, "클라이언트 인증 방식은 하나만 쓴다(Basic 과 client_secret 동시 전송)")
        }
        val decoded =
            try {
                String(Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length).trim()), StandardCharsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT, "Authorization 헤더 형식이 올바르지 않습니다")
            }
        val separator = decoded.indexOf(':')
        if (separator < 0) {
            throw OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT, "Authorization 헤더 형식이 올바르지 않습니다")
        }
        val (clientId, clientSecret) =
            try {
                URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8) to
                    URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                // 잘못된 % 인코딩(`%GG` 등) — 형식이 깨진 Basic 과 같은 invalid_client
                throw OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT, "Authorization 헤더 형식이 올바르지 않습니다")
            }
        if (!formClientId.isNullOrEmpty() && formClientId != clientId) {
            throw OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_REQUEST, "client_id 가 Authorization 헤더와 다릅니다")
        }
        return ClientCredentials(clientId = clientId, clientSecret = clientSecret)
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BASIC_PREFIX = "Basic "
    }
}