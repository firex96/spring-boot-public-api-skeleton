package com.rex.api.pub.skeleton.util

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

/**
 * 재시도 로직이 포함된 비동기 HTTP 클라이언트 유틸리티.
 *
 * 사용 예시:
 * ```kotlin
 * webClientUtil.postAsync(
 *     baseUrl = "https://api.example.com",
 *     uri = "/users",
 *     headers = HttpHeaders(),
 *     requestBody = request,
 *     responseType = UserResponse::class.java,
 *     maxRetryCount = 3,
 *     waitRetryTime = 2
 * ).subscribe { response -> ... }
 * ```
 */
@Component
class WebClientUtil(
    private val webClientBuilder: WebClient.Builder,
) {
    private fun createClient(baseUrl: String): WebClient =
        webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

    /**
     * 비동기 POST 요청 (재시도 로직 포함).
     *
     * @param baseUrl 기본 URL
     * @param uri 요청 URI
     * @param headers HTTP 헤더
     * @param requestBody 요청 본문
     * @param responseType 응답 타입
     * @param maxRetryCount 최대 재시도 횟수
     * @param waitRetryTime 재시도 대기 시간 (초)
     * @return 응답 Mono
     */
    fun <T : Any> postAsync(
        baseUrl: String,
        uri: String,
        headers: HttpHeaders,
        requestBody: Any,
        responseType: Class<T>,
        maxRetryCount: Int,
        waitRetryTime: Int,
    ): Mono<T> {
        val client = createClient(baseUrl)

        return client
            .post()
            .uri(uri)
            .headers { httpHeaders -> httpHeaders.addAll(headers) }
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(HttpStatusCode::is5xxServerError) { response ->
                response
                    .bodyToMono(String::class.java)
                    .flatMap { errorBody ->
                        if (isNonRetryableError(errorBody)) {
                            logFailure(uri, requestBody, errorBody)
                            Mono.error(NonRetryableException("즉시 실패: $errorBody"))
                        } else {
                            Mono.error(RetryableException("재시도 가능 오류: $errorBody"))
                        }
                    }
            }
            .bodyToMono(responseType)
            .retryWhen(
                Retry
                    .fixedDelay(maxRetryCount.toLong(), Duration.ofSeconds(waitRetryTime.toLong()))
                    .filter { ex: Throwable -> ex is RetryableException },
            ).doOnError { ex: Throwable ->
                log.error("WebClient 요청 실패: ${ex.message}", ex)
            }
    }

    /**
     * 비동기 GET 요청.
     *
     * @param baseUrl 기본 URL
     * @param uri 요청 URI
     * @param responseType 응답 타입
     * @param timeout 타임아웃 (초)
     * @return 응답 객체 (nullable)
     */
    fun <T : Any> get(
        baseUrl: String,
        uri: String,
        responseType: Class<T>,
        timeout: Long = DEFAULT_TIMEOUT_SECONDS,
    ): T? {
        val client = createClient(baseUrl)

        return try {
            client
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(responseType)
                .blockOptional(Duration.ofSeconds(timeout))
                .orElse(null)
        } catch (e: Exception) {
            log.error("GET 요청 실패: $baseUrl$uri", e)
            throw RuntimeException("GET 요청 실패: ${e.message}", e)
        }
    }

    /**
     * 재시도 불가능한 에러인지 확인.
     * 프로젝트 요구사항에 맞게 조건 수정 가능.
     */
    private fun isNonRetryableError(errorBody: String?): Boolean = errorBody?.contains("500X") == true

    private fun logFailure(
        uri: String,
        requestBody: Any?,
        errorMessage: String?,
    ) {
        log.error(
            """
            ❌ API 요청 실패
            URI: $uri
            Request: $requestBody
            Error: $errorMessage
            """.trimIndent(),
        )
    }

    /**
     * 재시도 가능한 예외.
     */
    class RetryableException(
        message: String,
    ) : RuntimeException(message)

    /**
     * 재시도 불가능한 예외 (즉시 실패).
     */
    class NonRetryableException(
        message: String,
    ) : RuntimeException(message)

    companion object {
        private val log = logger()
        private const val DEFAULT_TIMEOUT_SECONDS = 10L
    }
}