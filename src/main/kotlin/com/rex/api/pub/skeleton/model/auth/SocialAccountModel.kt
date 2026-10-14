package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import java.time.LocalDateTime

/** 연동된 소셜 계정 응답. */
data class SocialAccountModel(
    @field:JsonProperty("provider")
    val provider: SocialProvider,
    @field:JsonProperty("providerId")
    val providerId: String,
    @field:JsonProperty("email")
    val email: String?,
    @field:JsonProperty("name")
    val name: String?,
    @field:JsonProperty("connectedAt")
    val connectedAt: LocalDateTime,
)