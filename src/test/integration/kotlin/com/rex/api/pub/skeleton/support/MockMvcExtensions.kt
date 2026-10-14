package com.rex.api.pub.skeleton.support

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * MockMvc 확장 함수 모음.
 *
 * 통합 테스트에서 인증이 필요한 API를 간편하게 테스트할 수 있습니다.
 *
 * 사용 예시:
 * ```kotlin
 * mockMvc.getWithAuth("/api/users/me", token)
 *     .andExpect { status { isOk() } }
 *
 * mockMvc.postWithAuth("/api/users", token, createUserRequest)
 *     .andExpect { status { isCreated() } }
 * ```
 */

/** 테스트용 기본 ObjectMapper */
val testObjectMapper: JsonMapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

/**
 * 인증 헤더와 함께 GET 요청 수행.
 *
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 */
fun MockMvc.getWithAuth(
    url: String,
    token: String,
): ResultActionsDsl =
    get(url) {
        header("Authorization", "Bearer $token")
        accept = MediaType.APPLICATION_JSON
    }

/**
 * 인증 헤더와 함께 POST 요청 수행.
 *
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 * @param body 요청 본문 객체 (JSON으로 변환됨)
 */
fun MockMvc.postWithAuth(
    url: String,
    token: String,
    body: Any,
): ResultActionsDsl =
    post(url) {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        accept = MediaType.APPLICATION_JSON
        content = testObjectMapper.writeValueAsString(body)
    }

/**
 * 인증 헤더와 함께 PUT 요청 수행.
 *
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 * @param body 요청 본문 객체 (JSON으로 변환됨)
 */
fun MockMvc.putWithAuth(
    url: String,
    token: String,
    body: Any,
): ResultActionsDsl =
    put(url) {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        accept = MediaType.APPLICATION_JSON
        content = testObjectMapper.writeValueAsString(body)
    }

/**
 * 인증 헤더와 함께 DELETE 요청 수행.
 *
 * @param url 요청 URL
 * @param token JWT 액세스 토큰
 */
fun MockMvc.deleteWithAuth(
    url: String,
    token: String,
): ResultActionsDsl =
    delete(url) {
        header("Authorization", "Bearer $token")
        accept = MediaType.APPLICATION_JSON
    }

/**
 * 인증 없이 POST 요청 수행 (로그인, 회원가입 등).
 *
 * @param url 요청 URL
 * @param body 요청 본문 객체 (JSON으로 변환됨)
 */
fun MockMvc.postJson(
    url: String,
    body: Any,
): ResultActionsDsl =
    post(url) {
        contentType = MediaType.APPLICATION_JSON
        accept = MediaType.APPLICATION_JSON
        content = testObjectMapper.writeValueAsString(body)
    }

/**
 * 인증 없이 PUT 요청 수행.
 *
 * @param url 요청 URL
 * @param body 요청 본문 객체 (JSON으로 변환됨)
 */
fun MockMvc.putJson(
    url: String,
    body: Any,
): ResultActionsDsl =
    put(url) {
        contentType = MediaType.APPLICATION_JSON
        accept = MediaType.APPLICATION_JSON
        content = testObjectMapper.writeValueAsString(body)
    }

/**
 * 성공 상태 검증 (200 OK).
 */
fun ResultActionsDsl.expectSuccess(): ResultActionsDsl =
    andExpect { status { isOk() } }

/**
 * 생성 성공 상태 검증 (201 Created).
 */
fun ResultActionsDsl.expectCreated(): ResultActionsDsl =
    andExpect { status { isCreated() } }

/**
 * No Content 상태 검증 (204 No Content).
 */
fun ResultActionsDsl.expectNoContent(): ResultActionsDsl =
    andExpect { status { isNoContent() } }

/**
 * Bad Request 상태 검증 (400 Bad Request).
 */
fun ResultActionsDsl.expectBadRequest(): ResultActionsDsl =
    andExpect { status { isBadRequest() } }

/**
 * Unauthorized 상태 검증 (401 Unauthorized).
 */
fun ResultActionsDsl.expectUnauthorized(): ResultActionsDsl =
    andExpect { status { isUnauthorized() } }

/**
 * Forbidden 상태 검증 (403 Forbidden).
 */
fun ResultActionsDsl.expectForbidden(): ResultActionsDsl =
    andExpect { status { isForbidden() } }

/**
 * Not Found 상태 검증 (404 Not Found).
 */
fun ResultActionsDsl.expectNotFound(): ResultActionsDsl =
    andExpect { status { isNotFound() } }

/**
 * Conflict 상태 검증 (409 Conflict).
 */
fun ResultActionsDsl.expectConflict(): ResultActionsDsl =
    andExpect { status { isConflict() } }

/**
 * 응답 본문을 지정된 타입으로 파싱.
 *
 * @param T 파싱할 타입
 * @return 파싱된 객체
 */
inline fun <reified T> ResultActionsDsl.parseResponse(): T {
    var result: T? = null
    andReturn().let {
        val responseBody = it.response.contentAsString
        result = testObjectMapper.readValue(responseBody, T::class.java)
    }
    return result!!
}