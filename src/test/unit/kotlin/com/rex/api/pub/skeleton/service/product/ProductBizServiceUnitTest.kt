package com.rex.api.pub.skeleton.service.product

import com.rex.api.pub.skeleton.persistence.jdbc.service.main.ProductJdbcEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.main.ProductJpaEntityService
import com.rex.api.pub.skeleton.service.biz.ProductBizService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ProductBizServiceUnitTest : DescribeSpec({

    val productJpaEntityService = mockk<ProductJpaEntityService>()
    val productJdbcEntityService = mockk<ProductJdbcEntityService>()
    val service = ProductBizService(productJpaEntityService, productJdbcEntityService)

    describe("findAllProductIdByCategoryId") {

        it("should return list of product IDs for given category ID") {
            // given
            val categoryId = 100L
            val expectedProductIds = listOf(1L, 2L, 3L, 4L, 5L)

            every { productJdbcEntityService.getAllProductIdByCategoryId(categoryId) } returns expectedProductIds

            // when
            val result = service.findAllProductIdByCategoryId(categoryId)

            // then
            result shouldContainExactly expectedProductIds
            verify(exactly = 1) { productJdbcEntityService.getAllProductIdByCategoryId(categoryId) }
        }

        it("should return empty list when no product found for category ID") {
            // given
            val categoryId = 999L

            every { productJdbcEntityService.getAllProductIdByCategoryId(categoryId) } returns emptyList()

            // when
            val result = service.findAllProductIdByCategoryId(categoryId)

            // then
            result.shouldBeEmpty()
            verify(exactly = 1) { productJdbcEntityService.getAllProductIdByCategoryId(categoryId) }
        }

        it("should return single product ID when only one product in category") {
            // given
            val categoryId = 50L
            val expectedProductIds = listOf(42L)

            every { productJdbcEntityService.getAllProductIdByCategoryId(categoryId) } returns expectedProductIds

            // when
            val result = service.findAllProductIdByCategoryId(categoryId)

            // then
            result.size shouldBe 1
            result.first() shouldBe 42L
        }
    }
})