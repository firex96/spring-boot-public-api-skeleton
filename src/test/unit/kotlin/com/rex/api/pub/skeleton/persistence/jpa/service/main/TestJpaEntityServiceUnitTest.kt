package com.rex.api.pub.skeleton.persistence.jpa.service.main

import com.rex.api.pub.skeleton.dto.TestDto
import com.rex.api.pub.skeleton.mapper.TestMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.main.TestJpaEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.main.TestJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.Optional

class TestJpaEntityServiceUnitTest : DescribeSpec({

    val testJpaRepository = mockk<TestJpaRepository>()
    val mapper = mockk<TestMapper>()
    val service = TestJpaEntityService(testJpaRepository, mapper)

    beforeTest {
        clearMocks(testJpaRepository, mapper)
    }

    describe("get") {

        it("should return TestDto when entity exists") {
            // given
            val id = 1L
            val jsonData = listOf(mapOf("key1" to "value1"))
            val entity = mockk<TestJpaEntity>()
            val expectedDto = TestDto(id = id, json = jsonData)

            every { testJpaRepository.findById(id) } returns Optional.of(entity)
            every { mapper.toDto(entity) } returns expectedDto

            // when
            val result = service.get(id)

            // then
            result shouldBe expectedDto
            verify(exactly = 1) { testJpaRepository.findById(id) }
            verify(exactly = 1) { mapper.toDto(entity) }
        }

        it("should throw NoSuchElementException when entity does not exist") {
            // given
            val id = 999L

            every { testJpaRepository.findById(id) } returns Optional.empty()

            // when & then
            shouldThrow<NoSuchElementException> {
                service.get(id)
            }
        }
    }

    describe("create") {

        it("should create and return TestDto when id is 0") {
            // given
            val jsonData = listOf(mapOf("key1" to "value1"))
            val inputDto = TestDto(id = 0, json = jsonData)
            val entity = mockk<TestJpaEntity>()
            val savedEntity = mockk<TestJpaEntity>()
            val expectedDto = TestDto(id = 1, json = jsonData)

            every { mapper.toEntity(inputDto) } returns entity
            every { testJpaRepository.save(entity) } returns savedEntity
            every { mapper.toDto(savedEntity) } returns expectedDto

            // when
            val result = service.create(inputDto)

            // then
            result shouldBe expectedDto
            verify(exactly = 1) { testJpaRepository.save(entity) }
        }

        it("should throw IllegalArgumentException when id is not 0") {
            // given
            val inputDto = TestDto(id = 123, json = listOf(mapOf("key" to "value")))

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                service.create(inputDto)
            }
            exception.message shouldBe "ID must be zero for new product creation"
        }
    }

    describe("modify") {

        it("should modify and return TestDto when entity exists") {
            // given
            val jsonData = listOf(mapOf("updated" to "value"))
            val inputDto = TestDto(id = 1, json = jsonData)
            val existingEntity = TestJpaEntity(id = 1, json = listOf(mapOf("original" to "value")))

            every { testJpaRepository.findById(inputDto.id) } returns Optional.of(existingEntity)
            every { testJpaRepository.save(existingEntity) } returns existingEntity
            every { mapper.toDto(existingEntity) } returns inputDto

            // when
            val result = service.modify(inputDto)

            // then
            result shouldBe inputDto
            existingEntity.json shouldBe jsonData
            verify(exactly = 1) { testJpaRepository.findById(inputDto.id) }
            verify(exactly = 1) { testJpaRepository.save(existingEntity) }
        }

        it("should throw NoSuchElementException when entity does not exist") {
            // given
            val inputDto = TestDto(id = 999, json = listOf(mapOf("key" to "value")))

            every { testJpaRepository.findById(inputDto.id) } returns Optional.empty()

            // when & then
            val exception = shouldThrow<NoSuchElementException> {
                service.modify(inputDto)
            }
            exception.message shouldBe "Test with id ${inputDto.id} not found"
        }
    }

    describe("delete") {

        it("should delete entity by id") {
            // given
            val id = 1L

            every { testJpaRepository.deleteById(id) } just runs

            // when
            service.delete(id)

            // then
            verify(exactly = 1) { testJpaRepository.deleteById(id) }
        }
    }
})