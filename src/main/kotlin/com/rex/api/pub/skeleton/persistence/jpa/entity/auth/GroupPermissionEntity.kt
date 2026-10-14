package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "role_permissions")
class GroupPermissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L,

    @Column(name = "role_id", nullable = false)
    var groupId: Long,

    @Column(name = "permission_id", nullable = false)
    var permissionId: Long,

    @Column(name = "granted_at", nullable = false)
    var grantedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "granted_by")
    var grantedBy: Long? = null,
)