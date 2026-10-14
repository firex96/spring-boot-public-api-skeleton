package com.rex.api.pub.skeleton.dto

import com.rex.api.pub.skeleton.enums.ProductStatus
import java.time.LocalDateTime

data class ProductDto(
    val id: Long = 0L,
    val status: ProductStatus,
    val name: String,
    val description: String?,
    val price: Long,
    val categoryId: Long?,
    val createdBy: Long,
    val createdAt: LocalDateTime = LocalDateTime.now(), // NOT NULL — persist 시 감사 리스너가 덮는 자리표시자
    val updatedBy: Long,
    val updatedAt: LocalDateTime = LocalDateTime.now(), // NOT NULL — persist 시 감사 리스너가 덮는 자리표시자
)