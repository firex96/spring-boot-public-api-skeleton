package com.rex.api.pub.skeleton.persistence.jpa.service.main

import com.rex.api.pub.skeleton.annotation.CacheableNullSafe
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.dto.ProductDto
import com.rex.api.pub.skeleton.mapper.ProductMapper
import com.rex.api.pub.skeleton.persistence.jpa.repository.main.ProductJpaRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true, transactionManager = "mainTransactionManager")
@Service
class ProductJpaEntityService(
    private val productJpaRepository: ProductJpaRepository,
    private val mapper: ProductMapper,
) {
    @CacheableNullSafe(value = ["ProductDto"], key = "#id")
    fun get(id: Long): ProductDto? = productJpaRepository.findByIdOrNull(
        id = id,
    )?.let {
        mapper.toDto(it)
    }

    @CacheableNullSafe(value = ["ProductDtoList"], key = "#categoryId")
    fun getAllByCategoryId(
        categoryId: Long,
    ): List<ProductDto> = productJpaRepository.getAllByCategoryId(
        categoryId = categoryId,
    ).let {
        mapper.toDtoList(it)
    }

    @CacheEvict(value = ["ProductDto", "ProductDtoList"], allEntries = true)
    @Transactional(transactionManager = "mainTransactionManager")
    fun create(dto: ProductDto): ProductDto {
        if (dto.id != 0L) {
            throw IllegalArgumentException("Product ID must be zero for new product creation")
        }

        return productJpaRepository.save(
            mapper.toEntity(dto),
        ).let {
            mapper.toDto(it)
        }
    }

    @CachePut(value = ["ProductDto"], key = "#dto.id")
    @CacheEvict(value = ["ProductDtoList"], allEntries = true)
    @Transactional(transactionManager = "mainTransactionManager")
    fun update(dto: ProductDto): ProductDto {
        productJpaRepository.findById(
            dto.id,
        ).orElseThrow {
            NoSuchElementException("Product with id ${dto.id} not found")
        }

        return productJpaRepository.save(
            mapper.toEntity(dto),
        ).let {
            mapper.toDto(it)
        }
    }

    @CacheEvict(value = ["ProductDto", "ProductDtoList"], allEntries = true)
    @Transactional(transactionManager = "mainTransactionManager")
    fun delete(productId: Long) {
        productJpaRepository.deleteById(productId)
    }
}