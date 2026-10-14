package com.rex.api.pub.skeleton.enums.example

/**
 * 비동기 요청 상태 Enum.
 */
enum class RequestStatus(
    val description: String,
) {
    /** 요청 접수됨 */
    PENDING("대기중"),

    /** 처리 중 */
    PROCESSING("처리중"),

    /** 성공적으로 완료 */
    COMPLETED("완료"),

    /** 실패 (재시도 가능) */
    FAILED("실패"),

    /** 최종 실패 (재시도 소진) */
    EXHAUSTED("재시도 소진"),

    /** 취소됨 */
    CANCELLED("취소됨"),
}