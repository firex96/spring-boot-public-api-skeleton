package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

/** 로그인 토큰 응답 — `POST /auth/login` · 소셜 로그인. */
data class TokenResponseModel(
    @field:JsonProperty("accessToken")
    val accessToken: String,
    @field:JsonProperty("refreshToken")
    val refreshToken: String,
    @field:JsonProperty("tokenType")
    val tokenType: String,
    @field:JsonProperty("expiresIn")
    val expiresIn: Long,
)