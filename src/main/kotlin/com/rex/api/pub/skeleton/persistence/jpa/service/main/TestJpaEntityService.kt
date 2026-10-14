package com.rex.api.pub.skeleton.persistence.jpa.service.main

import com.rex.api.pub.skeleton.annotation.CacheableNullSafe
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.dto.TestDto
import com.rex.api.pub.skeleton.mapper.TestMapper
import com.rex.api.pub.skeleton.persistence.jpa.repository.main.TestJpaRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true, transactionManager = "mainTransactionManager")
@Service
class TestJpaEntityService(
    private val testJpaRepository: TestJpaRepository,
    private val mapper: TestMapper,
) {
    @CacheableNullSafe(value = ["TestDto"], key = "#id")
    fun get(id: Long): TestDto =
        testJpaRepository.findByIdOrNull(id)?.let {
            mapper.toDto(it)
        } ?: throw NoSuchElementException("Id($id) is not found")

    @Transactional(transactionManager = "mainTransactionManager")
    fun create(dto: TestDto): TestDto {
        if (dto.id != 0L) {
            throw IllegalArgumentException("ID must be zero for new product creation")
        }
        val entity = mapper.toEntity(dto)
        return testJpaRepository.save(entity).let {
            mapper.toDto(it)
        }
    }

    @CachePut(value = ["TestDto"], key = "#dto.id")
    @Transactional(transactionManager = "mainTransactionManager")
    fun modify(dto: TestDto): TestDto {
        val id = dto.id
        val existingEntity = testJpaRepository.findById(id).orElseThrow {
            NoSuchElementException("Test with id ${dto.id} not found")
        }
        // 기존 엔티티의 필드를 직접 업데이트
        existingEntity.json = dto.json

        return testJpaRepository.save(existingEntity).let {
            mapper.toDto(it)
        }
    }

    @CacheEvict(value = ["TestDto"], key = "#id")
    @Transactional(transactionManager = "mainTransactionManager")
    fun delete(id: Long) {
        testJpaRepository.deleteById(id)
    }
}