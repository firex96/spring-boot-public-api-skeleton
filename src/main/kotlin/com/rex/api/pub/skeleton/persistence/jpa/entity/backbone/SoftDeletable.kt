package com.rex.api.pub.skeleton.persistence.jpa.entity.backbone

import java.time.LocalDateTime

/**
 * Soft Delete를 지원하는 엔티티를 위한 인터페이스.
 *
 * 테이블별로 컬럼명이 다를 수 있으므로 BaseEntity에 포함하지 않고
 * 개별 엔티티에서 구현합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @Entity
 * @Table(name = "example")
 * @Where(clause = "deleted_at IS NULL")  // 조회 시 삭제된 데이터 자동 제외
 * class ExampleEntity : BaseEntity(), SoftDeletable {
 *     @Column(name = "deleted_at")
 *     override var deletedAt: LocalDateTime? = null
 *
 *     @Column(name = "deleted_by")
 *     override var deletedBy: Long? = null
 * }
 * ```
 *
 * Repository 사용:
 * ```kotlin
 * // @Where로 인해 삭제된 데이터는 자동 제외됨
 * repository.findAll()
 *
 * // 삭제된 데이터 포함 조회 시 Native Query 사용
 * @Query("SELECT * FROM example", nativeQuery = true)
 * fun findAllIncludingDeleted(): List<ExampleEntity>
 * ```
 */
interface SoftDeletable {
    /** 삭제 일시 (null이면 활성 상태) */
    var deletedAt: LocalDateTime?

    /** 삭제한 사용자 ID (선택적) */
    var deletedBy: Long?

    /** 삭제 여부 확인 */
    fun isDeleted(): Boolean = deletedAt != null

    /** Soft Delete 수행 */
    fun softDelete(deletedBy: Long? = null) {
        this.deletedAt = LocalDateTime.now()
        this.deletedBy = deletedBy
    }

    /** Soft Delete 복원 */
    fun restore() {
        this.deletedAt = null
        this.deletedBy = null
    }
}