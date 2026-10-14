package com.rex.api.pub.skeleton.service.example

import com.rex.api.pub.skeleton.client.example.ExampleWebClient
import com.rex.api.pub.skeleton.dto.example.AsyncProxyResultDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.enums.example.RequestStatus
import com.rex.api.pub.skeleton.util.logger
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 비동기 프록시 예제의 백그라운드 처리기.
 *
 * `@Async` 는 프록시가 가로채는 호출에서만 비동기다 — `ExampleAsyncProxyBizService` 와 **다른 빈**이라 서비스가 [process] 를 부르면
 * 프록시를 지나 `AsyncConfiguration.taskExecutor` 에서 돈다. 같은 클래스 안에서 `@Async` 메서드를 부르면 동기로 돈다
 * (`.claude/rules/conventions.md` 「데이터베이스」 3 · `AsyncSelfInvocationConventionTest`).
 */
@Component
class ExampleAsyncProxyWorker(
    private val exampleWebClient: ExampleWebClient,
    private val store: ExampleAsyncProxyStateStore,
) {
    companion object {
        private val logger = logger()
        private const val MAX_RETRY_COUNT = 3
        private val RETRYABLE_STATES = setOf(RequestStatus.PENDING, RequestStatus.FAILED)
    }

    /**
     * 백그라운드 처리 — 외부 API 를 부르고 상태·결과를 저장소에 남긴다.
     */
    @Async
    fun process(
        requestId: String,
        request: InternalUserRequestDto,
    ) {
        logger.info("백그라운드 처리 시작: requestId=$requestId")

        // PENDING(첫 처리)·FAILED(재시도) → PROCESSING 점유에 실패하면(취소가 먼저 이겼거나 이미 처리 중·종료) 외부 호출 없이 끝낸다
        if (!store.tryTransition(requestId, RETRYABLE_STATES, RequestStatus.PROCESSING, "처리 중입니다.")) {
            logger.info("처리 점유 실패(취소 또는 중복): requestId=$requestId, 현재 상태=${store.status(requestId)?.status}")
            return
        }

        try {
            val response = exampleWebClient.registerUser(request)

            // 결과와 종료 상태는 store.finish 한 번으로 — 재시도 실행과 결과가 엇갈리지 않는다
            if (response.success) {
                store.finish(
                    requestId,
                    RequestStatus.COMPLETED,
                    "처리가 완료되었습니다.",
                    AsyncProxyResultDto(
                        requestId = requestId,
                        success = true,
                        status = RequestStatus.COMPLETED,
                        response = response,
                    ),
                )
            } else {
                store.finish(
                    requestId,
                    RequestStatus.FAILED,
                    response.message,
                    AsyncProxyResultDto(
                        requestId = requestId,
                        success = false,
                        status = RequestStatus.FAILED,
                        response = response,
                        message = response.message,
                    ),
                )
            }

            logger.info("백그라운드 처리 완료: requestId=$requestId, success=${response.success}")
        } catch (e: Exception) {
            logger.error("백그라운드 처리 실패: requestId=$requestId", e)

            val retryCount = store.incrementRetryCount(requestId)
            val finalStatus = if (retryCount >= MAX_RETRY_COUNT) RequestStatus.EXHAUSTED else RequestStatus.FAILED

            store.finish(
                requestId = requestId,
                status = finalStatus,
                message = "처리 중 오류가 발생했습니다: ${e.message}",
                result =
                AsyncProxyResultDto(
                    requestId = requestId,
                    success = false,
                    status = finalStatus,
                    message = e.message,
                ),
                errorDetails = e.stackTraceToString(),
            )
        }
    }
}