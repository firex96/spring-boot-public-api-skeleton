package com.rex.api.pub.skeleton.service.biz

import com.rex.api.pub.skeleton.dto.ProductDto
import com.rex.api.pub.skeleton.persistence.jdbc.service.main.ProductJdbcEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.main.ProductJpaEntityService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true, transactionManager = "mainTransactionManager")
@Service
class ProductBizService(
    private val productJpaEntityService: ProductJpaEntityService,
    private val productJdbcEntityService: ProductJdbcEntityService,
) {
    fun find(id: Long): ProductDto? = productJpaEntityService.get(id)

    @Transactional(transactionManager = "mainTransactionManager")
    fun add(productDto: ProductDto): ProductDto = productJpaEntityService.create(productDto)

    @Transactional(transactionManager = "mainTransactionManager")
    fun modify(productDto: ProductDto): ProductDto = productJpaEntityService.update(productDto)

    @Transactional(transactionManager = "mainTransactionManager")
    fun remove(productId: Long) = productJpaEntityService.delete(productId)

    fun findAllProductIdByCategoryId(categoryId: Long): List<Long> = productJdbcEntityService.getAllProductIdByCategoryId(categoryId)
}