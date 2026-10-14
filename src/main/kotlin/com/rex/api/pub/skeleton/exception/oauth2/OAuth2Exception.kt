package com.rex.api.pub.skeleton.exception.oauth2

/**
 * OAuth2 관련 예외
 *
 * @param error OAuth2 에러 코드 (예: invalid_request, invalid_client, invalid_grant 등)
 * @param errorDescription 에러 설명
 * @param errorUri 에러 관련 URI (선택)
 */
class OAuth2Exception(
    val error: String,
    val errorDescription: String? = null,
    val errorUri: String? = null,
) : RuntimeException(errorDescription ?: error)