package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserGroupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserGroupRepository : JpaRepository<UserGroupEntity, Long> {
    fun findByUserIdAndGroupId(
        userId: Long,
        groupId: Long,
    ): Optional<UserGroupEntity>

    fun findByUserId(userId: Long): List<UserGroupEntity>

    fun findByGroupId(groupId: Long): List<UserGroupEntity>

    fun deleteByUserIdAndGroupId(
        userId: Long,
        groupId: Long,
    )

    fun existsByUserIdAndGroupId(
        userId: Long,
        groupId: Long,
    ): Boolean

    @Query(
        """
        SELECT ug FROM UserGroupEntity ug
        WHERE ug.userId = :userId
        AND (ug.expiresAt IS NULL OR ug.expiresAt > CURRENT_TIMESTAMP)
    """,
    )
    fun findActiveByUserId(
        @Param("userId") userId: Long,
    ): List<UserGroupEntity>
}