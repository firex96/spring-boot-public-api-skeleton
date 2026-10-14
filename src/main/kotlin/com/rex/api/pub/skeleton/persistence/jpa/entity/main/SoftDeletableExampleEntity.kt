package com.rex.api.pub.skeleton.persistence.jpa.entity.main

import com.rex.api.pub.skeleton.persistence.jpa.entity.backbone.BaseAuditedEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.backbone.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * Soft Delete 사용 예시 엔티티.
 *
 * @SQLRestriction으로 삭제된 데이터는 조회에서 자동 제외됩니다.
 * 실제 테이블에 따라 컬럼명을 변경해서 사용하세요.
 *
 * 주의: Hibernate 6.3+에서는 @Where 대신 @SQLRestriction 사용
 */
@Entity
@Table(name = "soft_deletable_example")
@SQLRestriction("deleted_at IS NULL")
class SoftDeletableExampleEntity(
    @Column(name = "name", nullable = false)
    var name: String,
) : BaseAuditedEntity(),
    SoftDeletable {
    @Column(name = "deleted_at")
    override var deletedAt: LocalDateTime? = null

    @Column(name = "deleted_by")
    override var deletedBy: Long? = null
}