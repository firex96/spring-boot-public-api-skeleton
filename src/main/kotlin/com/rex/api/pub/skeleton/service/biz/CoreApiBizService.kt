package com.rex.api.pub.skeleton.service.biz

import com.rex.api.pub.skeleton.client.core.CoreApiClient
import com.rex.api.pub.skeleton.client.core.response.ProductResponse
import org.springframework.stereotype.Service

/**
 * Core API 조회 비즈니스 서비스 — 외부 어댑터(`CoreApiClient`)는 Repository 와 같은 자리라 MappingService 가 직접 주입하지 않고 이 계층을 거친다.
 */
@Service
class CoreApiBizService(
    private val coreApiClient: CoreApiClient,
) {
    fun getProduct(productId: Long): ProductResponse = coreApiClient.getProduct(productId)
}