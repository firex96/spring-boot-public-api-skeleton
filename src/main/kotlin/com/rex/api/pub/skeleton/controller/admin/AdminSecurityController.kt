package com.rex.api.pub.skeleton.controller.admin

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.security.BlockIpRequestDto
import com.rex.api.pub.skeleton.dto.security.UnlockAccountRequestDto
import com.rex.api.pub.skeleton.model.security.BlockedIpModel
import com.rex.api.pub.skeleton.model.security.IpBlockStatusModel
import com.rex.api.pub.skeleton.model.security.LoginAttemptStatusModel
import com.rex.api.pub.skeleton.model.security.SecuritySettingsModel
import com.rex.api.pub.skeleton.model.user.MessageResponseModel
import com.rex.api.pub.skeleton.service.mapping.SecurityMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Security", description = "보안 관리 API (IP 차단, 계정 잠금)")
@RestController
@RequestMapping("/admin/security")
@PreAuthorize("hasPermission(null, 'admin:security:manage')")
class AdminSecurityController(
    private val mappingService: SecurityMappingService,
) {
    // ===== IP 차단 관리 =====

    @Operation(summary = "차단된 IP 목록 조회")
    @GetMapping("/blocked-ips")
    fun getBlockedIps(
        @PageableDefault(size = 20, sort = ["blockedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): ResponseEntity<Page<BlockedIpModel>> =
        ResponseEntity.ok(mappingService.getBlockedIps(pageable))

    @Operation(summary = "IP 차단")
    @PostMapping("/blocked-ips")
    fun blockIp(
        @Valid @RequestBody request: BlockIpRequestDto,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<BlockedIpModel> {
        val blockedIp = mappingService.blockIp(request, user.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(blockedIp)
    }

    @Operation(summary = "IP 차단 해제")
    @DeleteMapping("/blocked-ips/{id}")
    fun unblockIp(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Void> {
        mappingService.unblockIp(id, user.userId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "IP 주소로 차단 해제")
    @DeleteMapping("/blocked-ips/ip/{ipAddress}")
    fun unblockIpByAddress(
        @PathVariable ipAddress: String,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Void> {
        mappingService.unblockIpByAddress(ipAddress, user.userId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "IP 차단 상태 확인")
    @GetMapping("/blocked-ips/check/{ipAddress}")
    fun checkIpBlocked(@PathVariable ipAddress: String): ResponseEntity<IpBlockStatusModel> =
        ResponseEntity.ok(mappingService.checkIpBlocked(ipAddress))

    // ===== 계정 잠금 관리 =====

    @Operation(summary = "계정 잠금 해제")
    @PostMapping("/accounts/unlock")
    fun unlockAccount(
        @Valid @RequestBody request: UnlockAccountRequestDto,
    ): ResponseEntity<MessageResponseModel> {
        mappingService.unlockAccount(request.username)
        return ResponseEntity.ok(MessageResponseModel("계정 잠금이 해제되었습니다: ${request.username}"))
    }

    @Operation(summary = "계정 로그인 시도 상태 확인")
    @GetMapping("/accounts/status/{username}")
    fun getAccountStatus(@PathVariable username: String): ResponseEntity<LoginAttemptStatusModel> =
        ResponseEntity.ok(mappingService.getAccountStatus(username))

    // ===== 보안 설정 조회 =====

    @Operation(summary = "현재 보안 설정 조회")
    @GetMapping("/settings")
    fun getSecuritySettings(): ResponseEntity<SecuritySettingsModel> =
        ResponseEntity.ok(mappingService.getSecuritySettings())
}