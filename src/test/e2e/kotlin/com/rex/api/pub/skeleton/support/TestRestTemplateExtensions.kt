package com.rex.api.pub.skeleton.support

import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * TestRestTemplate 확장 함수 모음.
 *
 * E2E 테스트에서 인증이 필요한 API를 간편하게 테스트할 수 있습니다.
 *
 * 사용 예시:
 * ```kotlin
 * val response = restTemplate.getWithAuth<UserInfoDto>("/users/me", token)
 * val response = restTemplate.postWithAuth<TokenResponseDto>("/auth/login", loginRequest)
 * ```
 */

/**
 * 인증 헤더가 포함된 HttpHeaders 생성.
 */
fun createAuthHeaders(token: String): HttpHeaders =
    HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        accept = listOf(MediaType.APPLICATION_JSON)
        set("Authorization", "Bearer $token")
    }

/**
 * JSON 요청용 기본 HttpHeaders 생성 (인증 없음).
 */
fun createJsonHeaders(): HttpHeaders =
    HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        accept = listOf(MediaType.APPLICATION_JSON)
    }

/**
 * 인증 헤더와 함께 GET 요청 수행.
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.getWithAuth(
    url: String,
    token: String,
): ResponseEntity<T> {
    val headers = createAuthHeaders(token)
    val entity = HttpEntity<Void>(headers)
    return exchange(url, HttpMethod.GET, entity, T::class.java)
}

/**
 * 인증 헤더와 함께 POST 요청 수행.
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 * @param body 요청 본문
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.postWithAuth(
    url: String,
    token: String,
    body: Any,
): ResponseEntity<T> {
    val headers = createAuthHeaders(token)
    val entity = HttpEntity(body, headers)
    return exchange(url, HttpMethod.POST, entity, T::class.java)
}

/**
 * 인증 헤더와 함께 PUT 요청 수행.
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 * @param body 요청 본문
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.putWithAuth(
    url: String,
    token: String,
    body: Any,
): ResponseEntity<T> {
    val headers = createAuthHeaders(token)
    val entity = HttpEntity(body, headers)
    return exchange(url, HttpMethod.PUT, entity, T::class.java)
}

/**
 * 인증 헤더와 함께 DELETE 요청 수행.
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.deleteWithAuth(
    url: String,
    token: String,
): ResponseEntity<T> {
    val headers = createAuthHeaders(token)
    val entity = HttpEntity<Void>(headers)
    return exchange(url, HttpMethod.DELETE, entity, T::class.java)
}

/**
 * 인증 없이 JSON POST 요청 수행 (로그인, 회원가입 등).
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @param body 요청 본문
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.postJson(
    url: String,
    body: Any,
): ResponseEntity<T> {
    val headers = createJsonHeaders()
    val entity = HttpEntity(body, headers)
    return exchange(url, HttpMethod.POST, entity, T::class.java)
}

/**
 * 인증 없이 JSON GET 요청 수행.
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.getJson(
    url: String,
): ResponseEntity<T> {
    val headers = createJsonHeaders()
    val entity = HttpEntity<Void>(headers)
    return exchange(url, HttpMethod.GET, entity, T::class.java)
}

/**
 * form-urlencoded 요청용 HttpHeaders 생성 (인증 없음).
 */
fun createFormHeaders(): HttpHeaders =
    HttpHeaders().apply {
        contentType = MediaType.APPLICATION_FORM_URLENCODED
        accept = listOf(MediaType.APPLICATION_JSON)
    }

/**
 * form-urlencoded 요청용 HttpHeaders 생성 (인증 포함).
 */
fun createFormHeadersWithAuth(token: String): HttpHeaders =
    HttpHeaders().apply {
        contentType = MediaType.APPLICATION_FORM_URLENCODED
        accept = listOf(MediaType.APPLICATION_JSON)
        set("Authorization", "Bearer $token")
    }

/**
 * form-urlencoded POST 요청 수행 (OAuth2 토큰 엔드포인트용).
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @param params form 파라미터
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.postForm(
    url: String,
    params: org.springframework.util.MultiValueMap<String, String>,
): ResponseEntity<T> {
    val headers = createFormHeaders()
    val entity = HttpEntity(params, headers)
    return exchange(url, HttpMethod.POST, entity, T::class.java)
}

/**
 * 인증 헤더와 함께 form-urlencoded POST 요청 수행.
 *
 * @param T 응답 타입
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 * @param params form 파라미터
 * @return ResponseEntity<T>
 */
inline fun <reified T : Any> TestRestTemplate.postFormWithAuth(
    url: String,
    token: String,
    params: org.springframework.util.MultiValueMap<String, String>,
): ResponseEntity<T> {
    val headers = createFormHeadersWithAuth(token)
    val entity = HttpEntity(params, headers)
    return exchange(url, HttpMethod.POST, entity, T::class.java)
}