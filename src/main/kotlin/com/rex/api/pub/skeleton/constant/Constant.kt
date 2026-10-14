package com.rex.api.pub.skeleton.constant

/**
 * 애플리케이션 전역에서 사용되는 상수 정의.
 */
object Constant {
    /** MDC 키 - 요청 추적 ID */
    const val KEY_TRACE_ID = "traceId"

    /** MDC 키 - 요청 사용자 ID */
    const val KEY_USER_ID = "userId"

    /** 요청 헤더 - 사용자 ID */
    const val HEADER_USER_ID = "X-User-Id"

    /** 요청 헤더 - 추적 ID */
    const val HEADER_TRACE_ID = "X-Trace-Id"

    /** SQL IN 절 최대 아이템 수 (Oracle: 1000, MySQL: 제한 없음, 안전한 기본값) */
    const val IN_CONDITION_ITEM_SIZE = 1_000

    /** 시스템 사용자 ID (배치 작업, 스케줄러 등) */
    const val SYSTEM_USER_ID = 0L

    /** 페이지네이션 기본값 */
    object Pagination {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}