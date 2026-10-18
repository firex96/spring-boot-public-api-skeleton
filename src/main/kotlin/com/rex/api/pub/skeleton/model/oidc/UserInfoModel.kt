package com.rex.api.pub.skeleton.model.oidc

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * UserInfo Model (OIDC 표준)
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // 토큰 scope 가 허락하지 않은 클레임은 응답에서 빠진다
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
    @field:JsonProperty("updated_at")
    val updatedAt: Long?, // OIDC 선택 클레임
)