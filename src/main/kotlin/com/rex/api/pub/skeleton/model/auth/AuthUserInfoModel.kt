package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

/** 로그인 사용자 정보 응답 — `POST /auth/signup` · `GET /users/me`. 필드는 `dto.auth.UserInfoDto` 와 동일. OIDC userinfo 의 `model.oidc.UserInfoModel` 과 이름을 달리 한다 — springdoc 이 단순 이름으로 스키마를 만들어 같으면 문서가 뒤섞인다. */
data class AuthUserInfoModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("loginId")
    val loginId: String,
    @field:JsonProperty("email")
    val email: String?,
    @field:JsonProperty("name")
    val name: String?,
    @field:JsonProperty("nickname")
    val nickname: String?,
    @field:JsonProperty("phone")
    val phone: String?,
    @field:JsonProperty("profileImage")
    val profileImage: String?,
    @field:JsonProperty("groups")
    val groups: List<String>,
)