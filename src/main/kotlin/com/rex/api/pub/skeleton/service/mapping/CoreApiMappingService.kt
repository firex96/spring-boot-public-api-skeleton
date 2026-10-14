package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.mapper.ProductMapper
import com.rex.api.pub.skeleton.service.biz.CoreApiBizService
import org.springframework.stereotype.Service

@Service
class CoreApiMappingService(
    private val coreApiBizService: CoreApiBizService,
    private val mapper: ProductMapper,
) {
    fun getProduct(productId: Long) =
        coreApiBizService.getProduct(productId).let {
            mapper.toModel(it)
        }
}