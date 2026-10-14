package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_roles")
class UserGroupEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "role_id", nullable = false)
    var groupId: Long,

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "assigned_by")
    var assignedBy: Long? = null,

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,
)