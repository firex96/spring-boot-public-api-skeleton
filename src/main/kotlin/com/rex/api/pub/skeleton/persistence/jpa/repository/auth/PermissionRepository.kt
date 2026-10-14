package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.PermissionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, Long> {
    fun findByCode(code: String): Optional<PermissionEntity>

    fun findByResourceType(resourceType: String): List<PermissionEntity>

    fun existsByCode(code: String): Boolean

    @Query(
        """
        SELECT DISTINCT p FROM PermissionEntity p
        INNER JOIN GroupPermissionEntity gp ON p.id = gp.permissionId
        INNER JOIN UserGroupEntity ug ON gp.groupId = ug.groupId
        WHERE ug.userId = :userId
        AND (ug.expiresAt IS NULL OR ug.expiresAt > CURRENT_TIMESTAMP)
    """,
    )
    fun findAllByUserId(
        @Param("userId") userId: Long,
    ): List<PermissionEntity>
}