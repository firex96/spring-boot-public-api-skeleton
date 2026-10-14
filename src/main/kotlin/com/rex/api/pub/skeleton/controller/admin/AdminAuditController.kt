package com.rex.api.pub.skeleton.controller.admin

import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.model.audit.AuditLogModel
import com.rex.api.pub.skeleton.service.mapping.AuditMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(name = "Admin - Audit Log", description = "감사 로그 관리 API")
@RestController
@RequestMapping("/admin/audit")
@PreAuthorize("hasPermission(null, 'admin:audit:read')")
class AdminAuditController(
    private val mappingService: AuditMappingService,
) {
    @Operation(summary = "감사 로그 목록 조회")
    @GetMapping("/logs")
    fun getAuditLogs(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): ResponseEntity<Page<AuditLogModel>> =
        ResponseEntity.ok(mappingService.getAuditLogs(pageable))

    @Operation(summary = "감사 로그 검색")
    @GetMapping("/logs/search")
    fun searchAuditLogs(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) eventType: AuditEventType?,
        @RequestParam(required = false) eventCategory: String?,
        @RequestParam(required = false) result: AuditResult?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): ResponseEntity<Page<AuditLogModel>> = ResponseEntity.ok(
        mappingService.searchAuditLogs(
            userId = userId,
            eventType = eventType,
            eventCategory = eventCategory,
            result = result,
            startDate = startDate,
            endDate = endDate,
            pageable = pageable,
        ),
    )

    @Operation(summary = "사용자별 감사 로그 조회")
    @GetMapping("/logs/user/{userId}")
    fun getAuditLogsByUser(
        @PathVariable userId: Long,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): ResponseEntity<Page<AuditLogModel>> =
        ResponseEntity.ok(mappingService.getAuditLogsByUser(userId, pageable))

    @Operation(summary = "이벤트 타입별 감사 로그 조회")
    @GetMapping("/logs/event/{eventType}")
    fun getAuditLogsByEventType(
        @PathVariable eventType: AuditEventType,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): ResponseEntity<Page<AuditLogModel>> =
        ResponseEntity.ok(mappingService.getAuditLogsByEventType(eventType, pageable))

    @Operation(summary = "사용 가능한 이벤트 타입 목록")
    @GetMapping("/event-types")
    fun getEventTypes(): ResponseEntity<List<String>> =
        ResponseEntity.ok(mappingService.getEventTypes())

    @Operation(summary = "사용 가능한 이벤트 카테고리 목록")
    @GetMapping("/event-categories")
    fun getEventCategories(): ResponseEntity<List<String>> =
        ResponseEntity.ok(mappingService.getEventCategories())
}