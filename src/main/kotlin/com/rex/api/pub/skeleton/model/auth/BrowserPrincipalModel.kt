package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

/** 브라우저 세션에 로그인시킬 principal — 사용자 세션(access 토큰 jti)과 그룹·권한. 컨트롤러가 SecurityContext 로 옮긴다. */
data class BrowserPrincipalModel(
    @field:JsonProperty("userId")
    val userId: Long,
    @field:JsonProperty("sessionId")
    val sessionId: String,
    @field:JsonProperty("groups")
    val groups: List<String>,
    @field:JsonProperty("permissions")
    val permissions: List<String>,
)