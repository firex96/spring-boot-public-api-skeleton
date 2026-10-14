package com.rex.api.pub.skeleton.persistence.jpa.service.main

import com.rex.api.pub.skeleton.context.product.ProductBuilder
import com.rex.api.pub.skeleton.dto.ProductDto
import com.rex.api.pub.skeleton.mapper.ProductMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.main.ProductJpaEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.main.ProductJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.Optional

class ProductJpaEntityServiceUnitTest : DescribeSpec({

    val productJpaRepository = mockk<ProductJpaRepository>()
    val mapper = mockk<ProductMapper>()
    val service = ProductJpaEntityService(productJpaRepository, mapper)

    beforeTest {
        clearMocks(productJpaRepository, mapper)
    }

    describe("get") {

        it("should return ProductDto when entity exists") {
            // given
            val id = 1L
            val entity = mockk<ProductJpaEntity>()
            val expectedDto = ProductBuilder.productDtoBuilder().set(ProductDto::id, id).sample()

            every { productJpaRepository.findById(id) } returns Optional.of(entity)
            every { mapper.toDto(entity) } returns expectedDto

            // when
            val result = service.get(id)

            // then
            result shouldBe expectedDto
            verify(exactly = 1) { productJpaRepository.findById(id) }
            verify(exactly = 1) { mapper.toDto(entity) }
        }

        it("should return null when entity does not exist") {
            // given
            val id = 999L

            every { productJpaRepository.findById(id) } returns Optional.empty()

            // when
            val result = service.get(id)

            // then
            result.shouldBeNull()
        }
    }

    describe("getAllByCategoryId") {

        it("should return list of ProductDto for given category ID") {
            // given
            val categoryId = 100L
            val entities = listOf(mockk<ProductJpaEntity>(), mockk<ProductJpaEntity>())
            val expectedDtos = listOf(
                ProductBuilder.productDtoBuilder().set(ProductDto::categoryId, categoryId).sample(),
                ProductBuilder.productDtoBuilder().set(ProductDto::categoryId, categoryId).sample(),
            )

            every { productJpaRepository.getAllByCategoryId(categoryId) } returns entities
            every { mapper.toDtoList(entities) } returns expectedDtos

            // when
            val result = service.getAllByCategoryId(categoryId)

            // then
            result shouldHaveSize 2
            verify(exactly = 1) { productJpaRepository.getAllByCategoryId(categoryId) }
            verify(exactly = 1) { mapper.toDtoList(entities) }
        }

        it("should return empty list when no product found for category ID") {
            // given
            val categoryId = 999L

            every { productJpaRepository.getAllByCategoryId(categoryId) } returns emptyList()
            every { mapper.toDtoList(emptyList()) } returns emptyList()

            // when
            val result = service.getAllByCategoryId(categoryId)

            // then
            result.shouldBeEmpty()
        }
    }

    describe("create") {

        it("should create and return ProductDto when id is 0") {
            // given
            val inputDto = ProductBuilder.productDtoBuilder().set(ProductDto::id, 0).sample()
            val entity = mockk<ProductJpaEntity>()
            val savedEntity = mockk<ProductJpaEntity>()
            val expectedDto = inputDto.copy(id = 1)

            every { mapper.toEntity(inputDto) } returns entity
            every { productJpaRepository.save(entity) } returns savedEntity
            every { mapper.toDto(savedEntity) } returns expectedDto

            // when
            val result = service.create(inputDto)

            // then
            result shouldBe expectedDto
            verify(exactly = 1) { productJpaRepository.save(entity) }
        }

        it("should throw IllegalArgumentException when id is not 0") {
            // given
            val inputDto = ProductBuilder.productDtoBuilder().set(ProductDto::id, 123).sample()

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                service.create(inputDto)
            }
            exception.message shouldBe "Product ID must be zero for new product creation"
        }
    }

    describe("update") {

        it("should update and return ProductDto when entity exists") {
            // given
            val inputDto = ProductBuilder.productDtoBuilder().set(ProductDto::id, 1).sample()
            val existingEntity = mockk<ProductJpaEntity>()
            val updatedEntity = mockk<ProductJpaEntity>()
            val entityToSave = mockk<ProductJpaEntity>()

            every { productJpaRepository.findById(inputDto.id) } returns Optional.of(existingEntity)
            every { mapper.toEntity(inputDto) } returns entityToSave
            every { productJpaRepository.save(entityToSave) } returns updatedEntity
            every { mapper.toDto(updatedEntity) } returns inputDto

            // when
            val result = service.update(inputDto)

            // then
            result shouldBe inputDto
            verify(exactly = 1) { productJpaRepository.findById(inputDto.id) }
            verify(exactly = 1) { productJpaRepository.save(entityToSave) }
        }

        it("should throw NoSuchElementException when entity does not exist") {
            // given
            val inputDto = ProductBuilder.productDtoBuilder().set(ProductDto::id, 999).sample()

            every { productJpaRepository.findById(inputDto.id) } returns Optional.empty()

            // when & then
            val exception = shouldThrow<NoSuchElementException> {
                service.update(inputDto)
            }
            exception.message shouldBe "Product with id ${inputDto.id} not found"
        }
    }

    describe("delete") {

        it("should delete entity by id") {
            // given
            val productId = 1L

            every { productJpaRepository.deleteById(productId) } just runs

            // when
            service.delete(productId)

            // then
            verify(exactly = 1) { productJpaRepository.deleteById(productId) }
        }
    }
})