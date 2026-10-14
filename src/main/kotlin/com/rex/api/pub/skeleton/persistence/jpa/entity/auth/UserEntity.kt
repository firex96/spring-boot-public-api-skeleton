package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import com.rex.api.pub.skeleton.enums.auth.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "login_id", nullable = false, unique = true, length = 100)
    var loginId: String,

    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,

    @Column(name = "email", length = 255)
    var email: String? = null,

    @Column(name = "phone", length = 20)
    var phone: String? = null,

    @Column(name = "name", length = 100)
    var name: String? = null,

    @Column(name = "nickname", length = 100)
    var nickname: String? = null,

    @Column(name = "profile_image", length = 500)
    var profileImage: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by", length = 100)
    var createdBy: String? = null,

    @Column(name = "updated_by", length = 100)
    var updatedBy: String? = null,
)