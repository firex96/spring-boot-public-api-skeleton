package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "permissions")
class PermissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L,

    @Column(name = "code", nullable = false, unique = true, length = 100)
    var code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "description", length = 500)
    var description: String? = null,

    @Column(name = "resource_type", length = 50)
    var resourceType: String? = null,

    @Column(name = "action", length = 50)
    var action: String? = null,

    @Column(name = "is_system", nullable = false)
    var isSystem: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)