package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.PermissionEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.PermissionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

/** 권한 엔티티 서비스. 조회는 `PermissionDto`, 변경은 의도 메서드. 존재 검사·예외는 BizService 몫이다. */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class PermissionEntityService(
    private val permissionRepository: PermissionRepository,
    private val mapper: AccessControlMapper,
) {
    fun getAll(): List<PermissionDto> = mapper.toPermissionDtoList(permissionRepository.findAll())

    fun getById(id: Long): PermissionDto? = permissionRepository.findByIdOrNull(id)?.let { mapper.toPermissionDto(it) }

    fun getByCode(code: String): PermissionDto? =
        permissionRepository.findByCode(code).getOrNull()?.let { mapper.toPermissionDto(it) }

    fun getByResourceType(resourceType: String): List<PermissionDto> =
        mapper.toPermissionDtoList(permissionRepository.findByResourceType(resourceType))

    fun getAllByIds(ids: List<Long>): List<PermissionDto> =
        if (ids.isEmpty()) emptyList() else mapper.toPermissionDtoList(permissionRepository.findAllById(ids))

    /** 사용자가 속한(만료되지 않은) 그룹에 부여된 권한 전부. */
    fun getAllByUserId(userId: Long): List<PermissionDto> = mapper.toPermissionDtoList(permissionRepository.findAllByUserId(userId))

    fun existsById(id: Long): Boolean = permissionRepository.existsById(id)

    fun existsByCode(code: String): Boolean = permissionRepository.existsByCode(code)

    @Transactional(transactionManager = "authTransactionManager")
    fun create(code: String, name: String, description: String?, resourceType: String?, action: String?): PermissionDto {
        val permission =
            PermissionEntity(code = code, name = name, description = description, resourceType = resourceType, action = action, isSystem = false)
        return mapper.toPermissionDto(permissionRepository.save(permission))
    }

    /** null 인 `description` 은 유지한다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun update(id: Long, name: String, description: String?): PermissionDto {
        val permission = permissionRepository.findByIdOrNull(id) ?: throw NoSuchElementException("권한이 없습니다: $id")
        permission.name = name
        description?.let { permission.description = it }
        return mapper.toPermissionDto(permissionRepository.save(permission))
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun delete(id: Long) {
        permissionRepository.deleteById(id)
    }
}