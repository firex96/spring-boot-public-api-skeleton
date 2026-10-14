package com.rex.api.pub.skeleton.enums.example

/**
 * 사용자 상태 Enum.
 */
enum class UserStatus(
    val code: String,
    val description: String,
) {
    ACTIVE("A", "활성"),
    INACTIVE("I", "비활성"),
    PENDING("P", "대기중"),
    UNKNOWN("U", "알수없음"),
    ;

    companion object {
        /**
         * 외부 API 상태 코드를 내부 상태로 변환.
         */
        fun fromExternalCode(code: String): UserStatus =
            entries.find { it.code == code } ?: UNKNOWN
    }
}