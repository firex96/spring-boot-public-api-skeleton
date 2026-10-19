package com.rex.api.pub.skeleton.dto.auth

/** 막 발급한 1st-party 세션 — 사용자 세션 ID(= access 토큰 jti)와 토큰. */
data class IssuedSessionDto(
    val sessionId: String,
    val tokens: TokenResponseDto,
)