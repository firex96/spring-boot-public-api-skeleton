package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.RoleDto
import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.RoleEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.RoleRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/** 그룹(= Role) 엔티티 서비스. 조회는 `RoleDto`, 변경은 의도 메서드. 존재 검사·예외는 BizService 몫이다. */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class RoleEntityService(
    private val roleRepository: RoleRepository,
    private val mapper: AccessControlMapper,
) {
    fun getAll(): List<RoleDto> = mapper.toRoleDtoList(roleRepository.findAll())

    fun getById(id: Long): RoleDto? = roleRepository.findByIdOrNull(id)?.let { mapper.toRoleDto(it) }

    fun getByCode(code: String): RoleDto? = roleRepository.findByCode(code).getOrNull()?.let { mapper.toRoleDto(it) }

    fun getDefault(): RoleDto? = roleRepository.findByIsDefaultTrue().getOrNull()?.let { mapper.toRoleDto(it) }

    fun getAllByIds(ids: List<Long>): List<RoleDto> =
        if (ids.isEmpty()) emptyList() else mapper.toRoleDtoList(roleRepository.findAllById(ids))

    fun getCodesByIds(ids: List<Long>): List<String> =
        if (ids.isEmpty()) emptyList() else roleRepository.findAllById(ids).map { it.code }

    fun existsById(id: Long): Boolean = roleRepository.existsById(id)

    fun existsByCode(code: String): Boolean = roleRepository.existsByCode(code)

    @Transactional(transactionManager = "authTransactionManager")
    fun create(code: String, name: String, description: String?, isDefault: Boolean): RoleDto {
        val role = RoleEntity(code = code, name = name, description = description, isSystem = false, isDefault = isDefault)
        return mapper.toRoleDto(roleRepository.save(role))
    }

    /** null 인 `description`·`isDefault` 는 유지한다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun update(id: Long, name: String, description: String?, isDefault: Boolean?): RoleDto {
        val role = requireRole(id)
        role.name = name
        description?.let { role.description = it }
        isDefault?.let { role.isDefault = it }
        role.updatedAt = LocalDateTime.now()
        return mapper.toRoleDto(roleRepository.save(role))
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun clearDefault(id: Long) {
        val role = requireRole(id)
        role.isDefault = false
        role.updatedAt = LocalDateTime.now()
        roleRepository.save(role)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun delete(id: Long) {
        roleRepository.deleteById(id)
    }

    private fun requireRole(id: Long): RoleEntity =
        roleRepository.findByIdOrNull(id) ?: throw NoSuchElementException("그룹이 없습니다: $id") // 호출자가 먼저 존재를 확인한다
}