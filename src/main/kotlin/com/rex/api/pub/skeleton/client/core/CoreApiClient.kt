package com.rex.api.pub.skeleton.client.core

import com.rex.api.pub.skeleton.client.core.CoreApi
import org.springframework.stereotype.Component

@Component
class CoreApiClient(
    private val coreApi: CoreApi,
) {
    fun getProduct(productId: Long) = coreApi.getProduct(productId)
}