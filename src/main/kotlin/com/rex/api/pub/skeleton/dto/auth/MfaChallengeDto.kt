package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.MfaMethod

/** 1단계를 통과한 2차 인증 대상에게 주는 challenge — [token] 은 원본(Redis 에는 해시로만 둔다). */
data class MfaChallengeDto(
    val token: String,
    val expiresIn: Long,
    val methods: List<MfaMethod>,
)