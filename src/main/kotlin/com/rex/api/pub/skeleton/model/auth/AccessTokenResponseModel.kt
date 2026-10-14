package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

/** 액세스 토큰 재발급 응답 — `POST /auth/refresh`. */
data class AccessTokenResponseModel(
    @field:JsonProperty("accessToken")
    val accessToken: String,
    @field:JsonProperty("tokenType")
    val tokenType: String,
    @field:JsonProperty("expiresIn")
    val expiresIn: Long,
)