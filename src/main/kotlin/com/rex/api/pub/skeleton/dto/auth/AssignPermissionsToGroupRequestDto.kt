package com.rex.api.pub.skeleton.dto.auth

// 그룹에 권한 할당 요청
data class AssignPermissionsToGroupRequestDto(
    val permissionIds: List<Long>,
)