package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.GroupPermissionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupPermissionRepository : JpaRepository<GroupPermissionEntity, Long> {
    fun findByGroupId(groupId: Long): List<GroupPermissionEntity>

    fun findByPermissionId(permissionId: Long): List<GroupPermissionEntity>

    fun deleteByGroupIdAndPermissionId(
        groupId: Long,
        permissionId: Long,
    )

    fun deleteByGroupId(groupId: Long)

    fun existsByGroupIdAndPermissionId(
        groupId: Long,
        permissionId: Long,
    ): Boolean
}