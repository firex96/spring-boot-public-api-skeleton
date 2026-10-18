package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.user.UserProfileDto
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/**
 * 사용자 엔티티 서비스 — Entity 는 여기서 멈춘다.
 *
 * 조회는 DTO(`UserDto` 는 비밀번호 해시 포함 내부용, `UserProfileDto` 는 프로필)로 돌려주고,
 * 변경은 의도 메서드(`updatePassword`·`updateProfile`…)로 받는다. 세션은 `UserSessionEntityService`.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class UserEntityService(
    private val userRepository: UserRepository,
    private val mapper: AuthMapper,
) {
    fun getUserById(userId: Long): UserDto? =
        userRepository.findByIdOrNull(userId)?.let { mapper.toUserDto(it) }

    fun getUserByEmail(email: String): UserDto? =
        userRepository.findByEmail(email).getOrNull()?.let { mapper.toUserDto(it) }

    fun getActiveUserByLoginId(loginId: String): UserDto? =
        userRepository.findByLoginIdAndStatus(loginId, UserStatus.ACTIVE).getOrNull()?.let { mapper.toUserDto(it) }

    fun getUserProfileById(userId: Long, emailVerified: Boolean): UserProfileDto? =
        userRepository.findByIdOrNull(userId)?.let { mapper.toUserProfileDto(it, emailVerified) }

    fun existsById(userId: Long): Boolean = userRepository.existsById(userId)

    fun existsByLoginId(loginId: String): Boolean = userRepository.existsByLoginId(loginId)

    fun existsByEmail(email: String): Boolean = userRepository.existsByEmail(email)

    fun getExistingUserIds(userIds: List<Long>): Set<Long> =
        userRepository.findAllById(userIds).map { it.id }.toSet()

    @Transactional(transactionManager = "authTransactionManager")
    fun createUser(
        loginId: String,
        passwordHash: String,
        email: String?,
        name: String?,
        nickname: String?,
        phone: String?,
    ): UserDto {
        val user =
            UserEntity(
                loginId = loginId,
                passwordHash = passwordHash,
                email = email,
                name = name,
                nickname = nickname,
                phone = phone,
                status = UserStatus.ACTIVE,
            )
        return mapper.toUserDto(userRepository.save(user))
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun updatePassword(userId: Long, passwordHash: String) {
        val user = requireUser(userId)
        user.passwordHash = passwordHash
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun updateLastLoginAt(userId: Long, at: LocalDateTime) {
        val user = requireUser(userId)
        user.lastLoginAt = at
        userRepository.save(user)
    }

    /** null 인 인자는 유지한다 — 채울 값만 넘긴다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun updateProfile(
        userId: Long,
        name: String?,
        nickname: String?,
        phone: String?,
        profileImage: String?,
    ) {
        val user = requireUser(userId)
        name?.let { user.name = it }
        nickname?.let { user.nickname = it }
        phone?.let { user.phone = it }
        profileImage?.let { user.profileImage = it }
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun updateEmail(userId: Long, email: String) {
        val user = requireUser(userId)
        user.email = email
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
    }

    private fun requireUser(userId: Long): UserEntity =
        userRepository.findByIdOrNull(userId) ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")
}