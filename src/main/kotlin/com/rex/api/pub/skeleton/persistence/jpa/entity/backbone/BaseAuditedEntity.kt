package com.rex.api.pub.skeleton.persistence.jpa.entity.backbone

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 감사(Audit) 필드를 포함하는 기본 엔티티.
 *
 * JPA Auditing을 통해 생성/수정 시간과 사용자 정보가 자동으로 설정됩니다.
 *
 * @see AuditingEntityListener
 * @see com.rex.api.pub.skeleton.config.persistence.AuditConfiguration
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseAuditedEntity : BaseEntity() {
    @CreatedBy
    @Column(name = "created_by", updatable = false, nullable = false)
    var createdBy: Long = 0L

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now() // NOT NULL — 리스너가 persist 시 덮는다

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    var updatedBy: Long = 0L

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now() // NOT NULL — 리스너가 update 시 덮는다
}