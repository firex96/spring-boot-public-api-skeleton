package com.rex.api.pub.skeleton.service.example

import com.rex.api.pub.skeleton.dto.example.AsyncProxyResultDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.dto.example.ProxyRequestStatusDto
import com.rex.api.pub.skeleton.enums.example.RequestStatus
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 비동기 프록시 예제의 요청 상태·결과 저장소.
 *
 * 접수(`ExampleAsyncProxyBizService`)와 처리(`ExampleAsyncProxyWorker`)가 서로 다른 빈·다른 스레드에서 같은 상태를 다루므로
 * 저장소를 따로 뗐다. 프로덕션 환경에서는 데이터베이스로 교체한다.
 */
@Component
class ExampleAsyncProxyStateStore {
    private val statuses = ConcurrentHashMap<String, ProxyRequestStatusDto>()
    private val results = ConcurrentHashMap<String, AsyncProxyResultDto>()

    /** 새 요청을 PENDING 으로 등록하고 상태를 돌려준다. */
    fun open(request: InternalUserRequestDto): ProxyRequestStatusDto =
        ProxyRequestStatusDto(
            userId = request.userId,
            status = RequestStatus.PENDING,
            message = "요청이 접수되었습니다.",
        ).also { statuses[it.requestId] = it }

    fun status(requestId: String): ProxyRequestStatusDto? = statuses[requestId]

    fun result(requestId: String): AsyncProxyResultDto? = results[requestId]

    /**
     * 상태 전이를 **비교 후 교체**로 한다 — 현재 상태가 [from] 일 때만 [to] 로 바꾸고 true 를 돌려준다.
     *
     * 워커의 점유(`PENDING → PROCESSING`)와 취소(`PENDING → CANCELLED`)가 다른 스레드에서 경쟁하므로
     * 읽고-확인하고-쓰기가 아니라 `computeIfPresent` 한 번 안에서 판정한다. 승자는 하나다.
     */
    fun tryTransition(
        requestId: String,
        from: RequestStatus,
        to: RequestStatus,
        message: String,
    ): Boolean = tryTransition(requestId, setOf(from), to, message)

    /** [from] 집합 중 하나일 때만 전이 — 워커의 점유는 PENDING(첫 처리)과 FAILED(재시도) 둘에서 시작한다. */
    fun tryTransition(
        requestId: String,
        from: Set<RequestStatus>,
        to: RequestStatus,
        message: String,
    ): Boolean {
        var transitioned = false
        statuses.computeIfPresent(requestId) { _, current ->
            if (current.status !in from) {
                current
            } else {
                transitioned = true
                current.copy(
                    status = to,
                    message = message,
                    updatedAt = LocalDateTime.now(),
                    completedAt = if (to.isTerminal()) LocalDateTime.now() else null,
                    errorDetails = null,
                )
            }
        }
        return transitioned
    }

    /**
     * 처리 결과 공개 — **PROCESSING 을 쥔 실행만** 결과와 종료 상태를 함께 기록한다(같은 `computeIfPresent` 안).
     *
     * 재시도(`FAILED → PROCESSING`)가 결과 기록과 상태 전이 사이에 끼어들면 늦은 실행이 새 실행의 결과를 덮어쓴다 —
     * 그래서 결과 저장과 상태 전이를 한 번의 판정 안에서 하고, 소유권(PROCESSING)이 없는 실행의 publish 는 버린다(false).
     */
    fun finish(
        requestId: String,
        status: RequestStatus,
        message: String,
        result: AsyncProxyResultDto,
        errorDetails: String? = null,
    ): Boolean {
        var published = false
        statuses.computeIfPresent(requestId) { _, current ->
            if (current.status != RequestStatus.PROCESSING) {
                current
            } else {
                published = true
                results[requestId] = result
                current.copy(
                    status = status,
                    message = message,
                    updatedAt = LocalDateTime.now(),
                    completedAt = if (status.isTerminal()) LocalDateTime.now() else null,
                    errorDetails = errorDetails,
                )
            }
        }
        return published
    }

    /** 재시도 횟수를 1 올리고 새 값을 돌려준다 (요청이 없으면 0). */
    fun incrementRetryCount(requestId: String): Int {
        var newCount = 0
        statuses.computeIfPresent(requestId) { _, current ->
            newCount = current.retryCount + 1
            current.copy(retryCount = newCount, updatedAt = LocalDateTime.now())
        }
        return newCount
    }

    private fun RequestStatus.isTerminal(): Boolean =
        this in listOf(RequestStatus.COMPLETED, RequestStatus.EXHAUSTED, RequestStatus.CANCELLED)
}