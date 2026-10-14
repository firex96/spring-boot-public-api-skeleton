package com.rex.api.pub.skeleton.service.example

import com.rex.api.pub.skeleton.dto.example.AsyncProxyResultDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.dto.example.ProxyRequestStatusDto
import com.rex.api.pub.skeleton.enums.example.RequestStatus
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Service

/**
 * 비동기 프록시 서비스 예제 (상태 추적 기능 포함).
 *
 * 비동기 요청의 상태를 추적하고 나중에 결과를 조회할 수 있습니다.
 *
 * 주요 기능:
 * - 비동기 요청 접수 및 즉시 requestId 반환
 * - 백그라운드에서 외부 API 호출 ([ExampleAsyncProxyWorker])
 * - 요청 상태 추적 및 조회 ([ExampleAsyncProxyStateStore])
 * - 재시도 횟수 관리
 *
 * 사용 예시:
 * ```kotlin
 * // 1. 비동기 요청 접수
 * val requestId = asyncProxyService.submitRequest(request)
 *
 * // 2. 상태 조회
 * val status = asyncProxyService.getRequestStatus(requestId)
 *
 * // 3. 결과 조회 (완료된 경우)
 * val result = asyncProxyService.getResult(requestId)
 * ```
 *
 * `@Async` 는 별도 빈([ExampleAsyncProxyWorker])에 둔다 — 같은 클래스 안에서 부르면 프록시를 지나지 않아 동기로 돈다.
 * 프로덕션 환경에서는 상태를 데이터베이스에 저장하도록 구현을 변경해야 합니다.
 */
@Service
class ExampleAsyncProxyBizService(
    private val store: ExampleAsyncProxyStateStore,
    private val worker: ExampleAsyncProxyWorker,
) {
    companion object {
        private val logger = logger()
    }

    /**
     * 비동기 요청 접수.
     *
     * 요청을 접수하고 즉시 requestId를 반환합니다.
     * 실제 처리는 백그라운드에서 진행됩니다.
     *
     * @param request 내부 시스템 요청
     * @return 요청 추적용 ID
     */
    fun submitRequest(request: InternalUserRequestDto): String {
        val status = store.open(request)
        logger.info("비동기 요청 접수: requestId=${status.requestId}, userId=${request.userId}")

        // 다른 빈의 @Async 메서드 → 프록시를 지나 executor 로 넘어가고 즉시 돌아온다
        worker.process(status.requestId, request)

        return status.requestId
    }

    /**
     * 요청 상태 조회.
     *
     * @param requestId 요청 ID
     * @return 요청 상태 (없으면 null)
     */
    fun getRequestStatus(requestId: String): ProxyRequestStatusDto? = store.status(requestId)

    /**
     * 요청 결과 조회.
     *
     * @param requestId 요청 ID
     * @return 요청 결과 (처리 완료 전이면 null)
     */
    fun getResult(requestId: String): AsyncProxyResultDto? = store.result(requestId)

    /**
     * 요청 취소.
     *
     * 대기 중인 요청만 취소 가능합니다 — 워커가 이미 점유(PROCESSING)했으면 false.
     *
     * @param requestId 요청 ID
     * @return 취소 성공 여부
     */
    fun cancelRequest(requestId: String): Boolean {
        // 워커의 PENDING → PROCESSING 점유와 경쟁하므로 비교 후 교체 한 번으로 판정한다 — 둘 중 하나만 이긴다
        val cancelled = store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.CANCELLED, "요청이 취소되었습니다.")
        if (!cancelled) {
            logger.warn("취소 불가: requestId=$requestId, 현재 상태=${store.status(requestId)?.status}")
        }
        return cancelled
    }
}