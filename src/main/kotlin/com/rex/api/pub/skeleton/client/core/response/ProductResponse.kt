package com.rex.api.pub.skeleton.client.core.response

import com.rex.api.pub.skeleton.enums.ProductStatus

/**
 * Core API `GET /products/{productId}` 응답 — 외부 계약이라 필드 이름이 내부 Model 과 다를 수 있다(MapStruct 가 맞춘다).
 */
data class ProductResponse(
    val id: Long,
    val status: ProductStatus,
    val productName: String,
    val description: String?,
    val price: Long,
    val categoryId: Long?,
)