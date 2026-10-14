package com.rex.api.pub.skeleton.context.product

import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import com.rex.api.pub.skeleton.context.FixtureMonkeyFactory
import com.rex.api.pub.skeleton.dto.ProductDto
import com.rex.api.pub.skeleton.enums.ProductStatus
import net.jqwik.api.Arbitraries

/**
 * ProductDto 테스트 픽스처 빌더.
 */
object ProductBuilder {
    fun productDtoBuilder() =
        FixtureMonkeyFactory.fixtureMonkey
            .giveMeKotlinBuilder<ProductDto>()
            .setExp(ProductDto::status, Arbitraries.of(ProductStatus.entries))
            .setExp(ProductDto::name, Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200))
            .setExp(
                ProductDto::description,
                Arbitraries
                    .strings()
                    .alpha()
                    .numeric()
                    .whitespace()
                    .ofMaxLength(4000),
            ).setExp(ProductDto::price, Arbitraries.longs().between(0L, 10_000_000L))
}