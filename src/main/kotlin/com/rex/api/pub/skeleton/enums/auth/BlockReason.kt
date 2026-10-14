package com.rex.api.pub.skeleton.enums.auth

enum class BlockReason {
    BRUTE_FORCE, // Brute Force 공격
    SUSPICIOUS_ACTIVITY, // 의심스러운 활동
    MANUAL, // 관리자 수동 차단
    BOT_DETECTED, // 봇 감지
    RATE_LIMIT_EXCEEDED, // Rate Limit 초과
}