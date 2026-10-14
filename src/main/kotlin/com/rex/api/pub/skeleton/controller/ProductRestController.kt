package com.rex.api.pub.skeleton.controller

import com.rex.api.pub.skeleton.model.ProductModel
import com.rex.api.pub.skeleton.service.mapping.CoreApiMappingService
import com.rex.api.pub.skeleton.service.mapping.ProductMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "상품", description = "상품 조회 API")
@RequestMapping(path = ["/products"])
@RestController
class ProductRestController(
    private val mappingService: ProductMappingService,
    private val coreMappingService: CoreApiMappingService,
) {
    @Operation(
        summary = "상품 조회",
        description = "상품 ID 로 조회한다",
    )
    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable id: Long,
    ): ResponseEntity<ProductModel> = ResponseEntity.ok(mappingService.getProduct(id))

    @Operation(
        summary = "Core API 경유 상품 조회",
        description = "상품 ID 로 조회한다",
    )
    @GetMapping("/{id}/core")
    fun getCoreProduct(
        @PathVariable id: Long,
    ): ResponseEntity<ProductModel> = ResponseEntity.ok(coreMappingService.getProduct(id))
}