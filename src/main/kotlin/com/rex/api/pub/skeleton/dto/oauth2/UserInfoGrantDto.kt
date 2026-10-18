package com.rex.api.pub.skeleton.dto.oauth2

/** userinfo 에 쓸 수 있는 인가 서버 access 토큰의 주체와 scope — `OAuth2AuthorizationBizService.resolveUserInfoGrant`. */
data class UserInfoGrantDto(
    val userId: Long,
    val scopes: Set<String>,
)