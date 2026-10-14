package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 그룹-권한 매핑 응답
data class GroupPermissionDto(
    val groupId: Long,
    val permissionId: Long,
    val permissionCode: String,
    val permissionName: String,
    val grantedAt: LocalDateTime,
    val grantedBy: Long?,
)