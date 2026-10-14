package com.rex.api.pub.skeleton.config

import java.time.LocalDate
import java.time.LocalDateTime

/** 캐시 직렬화 테스트 픽스처 — final data class(타입 id 미부착 경로) 검증용. */
data class CacheSample(
    val id: Long,
    val name: String,
    val status: CacheSampleStatus,
    val createdAt: LocalDateTime,
    val validFrom: LocalDate,
)