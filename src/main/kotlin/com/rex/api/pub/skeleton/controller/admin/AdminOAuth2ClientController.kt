package com.rex.api.pub.skeleton.controller.admin

import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.UpdateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientWithSecretModel
import com.rex.api.pub.skeleton.service.mapping.OAuth2ClientMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - OAuth2 Client", description = "OAuth2 클라이언트 관리 API")
@RestController
@RequestMapping("/admin/oauth2/clients")
@PreAuthorize("hasPermission(null, 'oauth2:manage')")
class AdminOAuth2ClientController(
    private val oAuth2ClientMappingService: OAuth2ClientMappingService,
) {
    @Operation(summary = "전체 클라이언트 목록 조회")
    @GetMapping
    fun getAllClients(): ResponseEntity<List<OAuth2ClientModel>> {
        val clients = oAuth2ClientMappingService.getAllClients()
        return ResponseEntity.ok(clients)
    }

    @Operation(summary = "활성 클라이언트 목록 조회")
    @GetMapping("/active")
    fun getActiveClients(): ResponseEntity<List<OAuth2ClientModel>> {
        val clients = oAuth2ClientMappingService.getActiveClients()
        return ResponseEntity.ok(clients)
    }

    @Operation(summary = "클라이언트 상세 조회")
    @GetMapping("/{id}")
    fun getClient(@PathVariable id: Long): ResponseEntity<OAuth2ClientModel> {
        val client = oAuth2ClientMappingService.getClient(id)
        return ResponseEntity.ok(client)
    }

    @Operation(summary = "클라이언트 생성", description = "새 OAuth2 클라이언트를 생성합니다. client_secret은 생성 시 한 번만 반환됩니다.")
    @PostMapping
    fun createClient(
        @Valid @RequestBody request: CreateOAuth2ClientRequestDto,
    ): ResponseEntity<OAuth2ClientWithSecretModel> {
        val client = oAuth2ClientMappingService.createClient(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(client)
    }

    @Operation(summary = "클라이언트 수정")
    @PutMapping("/{id}")
    fun updateClient(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateOAuth2ClientRequestDto,
    ): ResponseEntity<OAuth2ClientModel> {
        val client = oAuth2ClientMappingService.updateClient(id, request)
        return ResponseEntity.ok(client)
    }

    @Operation(summary = "클라이언트 시크릿 재생성", description = "새 client_secret을 생성합니다. 기존 시크릿은 무효화됩니다.")
    @PostMapping("/{id}/secret")
    fun regenerateSecret(@PathVariable id: Long): ResponseEntity<OAuth2ClientWithSecretModel> {
        val client = oAuth2ClientMappingService.regenerateSecret(id)
        return ResponseEntity.ok(client)
    }

    @Operation(summary = "클라이언트 비활성화", description = "클라이언트를 비활성화하고 관련 토큰을 무효화합니다.")
    @PostMapping("/{id}/deactivate")
    fun deactivateClient(@PathVariable id: Long): ResponseEntity<OAuth2ClientModel> {
        val client = oAuth2ClientMappingService.deactivateClient(id)
        return ResponseEntity.ok(client)
    }

    @Operation(summary = "클라이언트 삭제", description = "클라이언트를 삭제하고 관련 토큰을 무효화합니다.")
    @DeleteMapping("/{id}")
    fun deleteClient(@PathVariable id: Long): ResponseEntity<Void> {
        oAuth2ClientMappingService.deleteClient(id)
        return ResponseEntity.noContent().build()
    }
}