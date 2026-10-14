package com.rex.api.pub.skeleton.client.example

import com.rex.api.pub.skeleton.dto.example.ExternalUserResponseDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.dto.example.InternalUserResponseDto
import com.rex.api.pub.skeleton.exception.ApiCallException
import com.rex.api.pub.skeleton.mapper.example.ExampleProxyMapper
import com.rex.api.pub.skeleton.util.WebClientUtil
import com.rex.api.pub.skeleton.util.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * WebClient를 이용한 외부 API 호출 서비스 예제.
 *
 * 외부 API 호출을 위한 서비스로, 다음 기능을 제공합니다:
 * - 요청/응답 포맷 변환
 * - 재시도 로직 (5xx 에러 시)
 * - 에러 변환 및 처리
 * - 비동기 처리 지원
 *
 * 사용 예시:
 * ```kotlin
 * // 동기 호출
 * val response = webClientService.registerUser(request)
 *
 * // 비동기 호출 (Fire-and-Forget)
 * webClientService.registerUserAsync(request)
 *     .doOnSuccess { logger.info("등록 성공: $it") }
 *     .subscribe()
 * ```
 */
@Component
class ExampleWebClient(
    private val webClientUtil: WebClientUtil,
    private val exampleProxyMapper: ExampleProxyMapper,
    @Value("\${example.proxy.base-url:http://localhost:8080}")
    private val baseUrl: String,
    @Value("\${example.proxy.retry-count:3}")
    private val maxRetryCount: Int,
    @Value("\${example.proxy.retry-wait-seconds:2}")
    private val retryWaitSeconds: Int,
) {
    companion object {
        private val logger = logger()
        private const val REGISTER_USER_URI = "/api/v1/users/register"
    }

    /**
     * 동기 방식 사용자 등록.
     *
     * 외부 API를 호출하여 사용자를 등록합니다.
     * 내부 포맷으로 변환된 응답을 반환합니다.
     *
     * @param request 내부 시스템 요청
     * @return 내부 시스템 응답
     * @throws ApiCallException 외부 API 호출 실패 시
     */
    fun registerUser(request: InternalUserRequestDto): InternalUserResponseDto {
        logger.info("사용자 등록 요청: userId=${request.userId}")

        return try {
            // 1. 내부 → 외부 포맷 변환
            val externalRequest = exampleProxyMapper.toExternalRequest(request)

            // 2. 외부 API 호출
            val externalResponse =
                webClientUtil.get(
                    baseUrl = baseUrl,
                    uri = REGISTER_USER_URI,
                    responseType = ExternalUserResponseDto::class.java,
                ) ?: throw ApiCallException("응답이 없습니다.", HttpStatus.BAD_GATEWAY)

            // 3. 외부 → 내부 포맷 변환
            val response = exampleProxyMapper.toInternalResponse(externalResponse)

            logger.info("사용자 등록 완료: userId=${request.userId}, success=${response.success}")
            response
        } catch (e: ApiCallException) {
            throw e
        } catch (e: Exception) {
            logger.error("사용자 등록 실패: userId=${request.userId}", e)
            throw ApiCallException("외부 API 호출 실패: ${e.message}", HttpStatus.BAD_GATEWAY, e)
        }
    }

    /**
     * 비동기 방식 사용자 등록 (재시도 로직 포함).
     *
     * 외부 API를 비동기로 호출하며, 5xx 에러 시 자동으로 재시도합니다.
     * Fire-and-Forget 패턴에 적합합니다.
     *
     * @param request 내부 시스템 요청
     * @return Mono<InternalUserResponse> 비동기 응답
     *
     * 사용 예시:
     * ```kotlin
     * webClientService.registerUserAsync(request)
     *     .doOnSuccess { response ->
     *         if (response.success) {
     *             // 성공 처리
     *         } else {
     *             // 비즈니스 실패 처리
     *         }
     *     }
     *     .doOnError { error ->
     *         // 시스템 에러 처리
     *     }
     *     .subscribe()
     * ```
     */
    fun registerUserAsync(request: InternalUserRequestDto): Mono<InternalUserResponseDto> {
        logger.info("사용자 등록 비동기 요청: userId=${request.userId}")

        // 1. 내부 → 외부 포맷 변환
        val externalRequest = exampleProxyMapper.toExternalRequest(request)

        // 2. 비동기 API 호출 (재시도 로직 포함)
        return webClientUtil
            .postAsync(
                baseUrl = baseUrl,
                uri = REGISTER_USER_URI,
                headers = createHeaders(),
                requestBody = externalRequest,
                responseType = ExternalUserResponseDto::class.java,
                maxRetryCount = maxRetryCount,
                waitRetryTime = retryWaitSeconds,
            ).map { externalResponse: ExternalUserResponseDto ->
                // 3. 외부 → 내부 포맷 변환
                exampleProxyMapper.toInternalResponse(externalResponse)
            }.doOnSuccess { response: InternalUserResponseDto? ->
                logger.info("사용자 등록 비동기 완료: userId=${request.userId}, success=${response?.success}")
            }.doOnError { error: Throwable ->
                logger.error("사용자 등록 비동기 실패: userId=${request.userId}", error)
            }
    }

    /**
     * 요청 헤더 생성.
     * 프로젝트 요구사항에 맞게 헤더 추가 가능.
     */
    private fun createHeaders(): HttpHeaders =
        HttpHeaders().apply {
            set("X-Request-Source", "internal-api")
            set("X-Client-Version", "1.0.0")
        }
}