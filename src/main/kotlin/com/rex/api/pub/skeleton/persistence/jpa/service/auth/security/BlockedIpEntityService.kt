package com.rex.api.pub.skeleton.persistence.jpa.service.auth.security

import com.rex.api.pub.skeleton.dto.security.BlockedIpDto
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.mapper.SecurityMapper
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.security.BlockedIpRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 차단된 IP 엔티티 서비스
 * Entity ↔ DTO 변환 및 Repository 호출 담당
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class BlockedIpEntityService(
    private val blockedIpRepository: BlockedIpRepository,
    private val mapper: SecurityMapper,
) {
    fun getById(id: Long): BlockedIpDto? =
        blockedIpRepository.findByIdOrNull(id)?.let { mapper.toDto(it) }

    fun getActiveBlockedIps(pageable: Pageable): Page<BlockedIpDto> =
        blockedIpRepository.findByIsActiveTrue(pageable).map { mapper.toDto(it) }

    fun getByBlockReason(blockReason: BlockReason, pageable: Pageable): Page<BlockedIpDto> =
        blockedIpRepository.findByBlockReason(blockReason, pageable).map { mapper.toDto(it) }

    fun getActiveBlockByIpAddress(ipAddress: String): BlockedIpDto? =
        blockedIpRepository.findByIpAddressAndIsActiveTrue(ipAddress)
            .map { mapper.toDto(it) }
            .orElse(null)

    fun getActiveBlock(ipAddress: String): BlockedIpDto? =
        blockedIpRepository.findActiveBlock(ipAddress, LocalDateTime.now())
            .map { mapper.toDto(it) }
            .orElse(null)

    fun isBlocked(ipAddress: String): Boolean =
        blockedIpRepository.isBlocked(ipAddress, LocalDateTime.now())

    @Transactional(transactionManager = "authTransactionManager")
    fun create(dto: BlockedIpDto): BlockedIpDto {
        val entity = mapper.toEntity(dto)
        return mapper.toDto(blockedIpRepository.save(entity))
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun unblock(id: Long, unblockedBy: Long?) {
        val entity = blockedIpRepository.findByIdOrNull(id) ?: throw ResourceNotFoundException("차단 정보를 찾을 수 없습니다: $id")
        entity.unblock(unblockedBy)
        blockedIpRepository.save(entity)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun unblockByIpAddress(ipAddress: String, unblockedBy: Long?) {
        val entity =
            blockedIpRepository.findByIpAddressAndIsActiveTrue(ipAddress).orElseThrow {
                ResourceNotFoundException("활성 차단 정보를 찾을 수 없습니다: $ipAddress")
            }
        entity.unblock(unblockedBy)
        blockedIpRepository.save(entity)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun deactivateExpiredBlocks(): Int =
        blockedIpRepository.deactivateExpiredBlocks(LocalDateTime.now())
}