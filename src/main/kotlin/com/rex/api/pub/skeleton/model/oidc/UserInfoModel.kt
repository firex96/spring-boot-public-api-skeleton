package com.rex.api.pub.skeleton.model.oidc

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * UserInfo Model (OIDC 표준)
 */
data class UserInfoModel(
    @field:JsonProperty("sub")
    val sub: String,
    @field:JsonProperty("name")
    val name: String?,
    @field:JsonProperty("nickname")
    val nickname: String?,
    @field:JsonProperty("picture")
    val picture: String?,
    @field:JsonProperty("email")
    val email: String?,
    @field:JsonProperty("email_verified")
    val emailVerified: Boolean?, // OIDC 선택 클레임 — DTO 가 null 이면 응답에서도 null (non-null 이면 MapStruct 가 false 로 둔갑시킨다)
    @field:JsonProperty("phone_number")
    val phoneNumber: String?,
    @field:JsonProperty("phone_number_verified")
    val phoneNumberVerified: Boolean?, // OIDC 선택 클레임
    @field:JsonProperty("updated_at")
    val updatedAt: Long?, // OIDC 선택 클레임
)