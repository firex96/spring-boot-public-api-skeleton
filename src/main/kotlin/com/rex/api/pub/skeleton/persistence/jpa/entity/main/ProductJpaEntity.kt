package com.rex.api.pub.skeleton.persistence.jpa.entity.main

import com.rex.api.pub.skeleton.converter.ProductStatusConverter
import com.rex.api.pub.skeleton.enums.ProductStatus
import com.rex.api.pub.skeleton.persistence.jpa.entity.backbone.BaseAuditedEntity
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 상품 예제 엔티티 (main 스키마).
 *
 * 기본 키 컬럼이 `id` 가 아닌 테이블을 매핑하는 예로 [AttributeOverride] 를 둔다 — 감사 컬럼은 공통 이름을 그대로 쓴다.
 */
@Entity
@AttributeOverride(name = "id", column = Column(name = "product_id"))
@Table(name = "product")
class ProductJpaEntity(
    @Convert(converter = ProductStatusConverter::class)
    @Column(name = "status", nullable = false, length = 50)
    var status: ProductStatus,
    @Column(name = "name", nullable = false, length = 200)
    var name: String,
    @Column(name = "description", length = 4000)
    var description: String?,
    @Column(name = "price", nullable = false)
    var price: Long,
    @Column(name = "category_id")
    var categoryId: Long?,
) : BaseAuditedEntity()