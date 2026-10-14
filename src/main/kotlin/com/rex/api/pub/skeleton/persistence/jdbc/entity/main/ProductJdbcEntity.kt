package com.rex.api.pub.skeleton.persistence.jdbc.entity.main

import com.rex.api.pub.skeleton.enums.ProductStatus
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * product (main 스키마) — Spring Data JDBC 엔티티.
 *
 * 타입 선언 규약: JdbcEntity 는 data class + val + 생성자 매핑. `?` 는 컬럼의 NULL 허용과 일치시킨다.
 * 기본값은 id 에만 둔다 — 전부 기본값이면 MapStruct 가 no-arg 생성자 + setter 경로로 떨어져 빈 객체를 만든다.
 */
@Table("product")
data class ProductJdbcEntity(
    @Id @Column("product_id") val id: Long = 0L, // 0 = 신규 (Spring Data JDBC 신규 판정)
    @Column("status") val status: ProductStatus,
    @Column("name") val name: String,
    @Column("description") val description: String?,
    @Column("price") val price: Long,
    @Column("category_id") val categoryId: Long?,
    @Column("created_by") val createdBy: Long,
    @Column("created_at") val createdAt: LocalDateTime,
    @Column("updated_by") val updatedBy: Long,
    @Column("updated_at") val updatedAt: LocalDateTime,
)