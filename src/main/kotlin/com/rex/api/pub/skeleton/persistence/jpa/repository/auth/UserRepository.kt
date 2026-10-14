package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByLoginId(loginId: String): Optional<UserEntity>

    fun findByEmail(email: String): Optional<UserEntity>

    fun existsByLoginId(loginId: String): Boolean

    fun existsByEmail(email: String): Boolean

    fun findByLoginIdAndStatus(
        loginId: String,
        status: UserStatus,
    ): Optional<UserEntity>
}