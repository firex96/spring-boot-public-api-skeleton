package com.rex.api.pub.skeleton.dto.auth

// 권한에 그룹 할당 요청
data class AssignGroupsToPermissionRequestDto(
    val groupIds: List<Long>,
)