package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_social_accounts")
class UserSocialAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "provider", nullable = false, length = 20)
    var provider: String,

    @Column(name = "provider_id", nullable = false, length = 255)
    var providerId: String,

    @Column(name = "email", length = 255)
    var email: String? = null,

    @Column(name = "name", length = 100)
    var name: String? = null,

    @Column(name = "profile_image", length = 500)
    var profileImage: String? = null,

    @Column(name = "access_token", length = 1000)
    var accessToken: String? = null,

    @Column(name = "refresh_token", length = 1000)
    var refreshToken: String? = null,

    @Column(name = "token_expires_at")
    var tokenExpiresAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)