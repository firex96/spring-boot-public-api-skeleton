package com.rex.api.pub.skeleton.dto.security

import com.rex.api.pub.skeleton.enums.auth.BlockReason
import jakarta.validation.constraints.NotBlank

data class BlockIpRequestDto(
    @field:NotBlank(message = "IP 주소는 필수입니다")
    val ipAddress: String,
    val reason: BlockReason = BlockReason.MANUAL,
    val description: String? = null,
    val durationMinutes: Int? = null, // null이면 영구 차단
)