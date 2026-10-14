package com.rex.api.pub.skeleton.persistence.jpa.service.main

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.dto.TestDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional

/**
 * TestJpaEntityService 통합 테스트.
 *
 * Testcontainers를 통해 MySQL 데이터베이스와 연동하여 테스트합니다.
 * - 실제 JPA Repository 사용
 * - 실제 MapStruct Mapper 사용
 * - 트랜잭션 롤백으로 테스트 격리
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.persistence.jpa.service.main.TestJpaEntityServiceIntegrationTest"
 * ```
 */
@SpringBootTest
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
@Transactional
class TestJpaEntityServiceIntegrationTest(
    private val service: TestJpaEntityService,
) : BehaviorSpec() {

    init {
        Given("Test 생성을 위한 TestDto") {
            val jsonData = listOf(
                mapOf("key1" to "value1", "key2" to "value2"),
                mapOf("key3" to "value3"),
            )
            val testDto = TestDto(id = 0, json = jsonData)

            When("create 메서드를 호출하면") {
                val createdTest = service.create(testDto)

                Then("ID가 생성된 TestDto가 반환된다") {
                    createdTest.shouldNotBeNull()
                    createdTest.id shouldBeGreaterThan 0L
                    createdTest.json shouldBe jsonData
                }
            }
        }

        Given("ID가 0이 아닌 TestDto") {
            val testDto = TestDto(id = 123, json = listOf(mapOf("key" to "value")))

            When("create 메서드를 호출하면") {
                Then("IllegalArgumentException이 발생한다") {
                    val exception = shouldThrow<IllegalArgumentException> {
                        service.create(testDto)
                    }
                    exception.message shouldBe "ID must be zero for new product creation"
                }
            }
        }

        Given("저장된 Test") {
            val jsonData = listOf(mapOf("test" to "data"))
            val testDto = TestDto(id = 0, json = jsonData)
            val savedTest = service.create(testDto)

            When("get 메서드로 ID로 조회하면") {
                val foundTest = service.get(savedTest.id)

                Then("저장된 Test가 조회된다") {
                    foundTest.shouldNotBeNull()
                    foundTest.id shouldBe savedTest.id
                    foundTest.json shouldBe jsonData
                }
            }

            When("존재하지 않는 ID로 조회하면") {
                Then("NoSuchElementException이 발생한다") {
                    shouldThrow<NoSuchElementException> {
                        service.get(999999)
                    }
                }
            }
        }

        Given("수정할 Test") {
            val originalJson = listOf(mapOf("original" to "data"))
            val testDto = TestDto(id = 0, json = originalJson)
            val savedTest = service.create(testDto)

            When("modify 메서드로 수정하면") {
                val updatedJson = listOf(
                    mapOf("updated" to "value"),
                    mapOf("new" to "data"),
                )
                val updatedDto = savedTest.copy(json = updatedJson)
                val result = service.modify(updatedDto)

                Then("수정된 Test가 반환된다") {
                    result.shouldNotBeNull()
                    result.id shouldBe savedTest.id
                    result.json shouldBe updatedJson
                }

                And("조회 시 수정된 값이 확인된다") {
                    val foundTest = service.get(savedTest.id)
                    foundTest.shouldNotBeNull()
                    foundTest.json shouldBe updatedJson
                }
            }
        }

        Given("존재하지 않는 Test ID로 modify 시도") {
            val nonExistentDto = TestDto(id = 999999, json = listOf(mapOf("key" to "value")))

            When("modify 메서드를 호출하면") {
                Then("NoSuchElementException이 발생한다") {
                    val exception = shouldThrow<NoSuchElementException> {
                        service.modify(nonExistentDto)
                    }
                    exception.message shouldBe "Test with id ${nonExistentDto.id} not found"
                }
            }
        }

        Given("삭제할 Test") {
            val jsonData = listOf(mapOf("delete" to "me"))
            val testDto = TestDto(id = 0, json = jsonData)
            val savedTest = service.create(testDto)

            When("delete 메서드로 삭제하면") {
                service.delete(savedTest.id)

                Then("조회 시 NoSuchElementException이 발생한다") {
                    shouldThrow<NoSuchElementException> {
                        service.get(savedTest.id)
                    }
                }
            }
        }

        Given("복잡한 JSON 데이터를 가진 Test") {
            val complexJson = listOf(
                mapOf("nested" to "value1", "array" to "item1"),
                mapOf("nested" to "value2", "array" to "item2"),
                mapOf("nested" to "value3", "array" to "item3"),
            )
            val testDto = TestDto(id = 0, json = complexJson)

            When("create 후 조회하면") {
                val createdTest = service.create(testDto)
                val foundTest = service.get(createdTest.id)

                Then("JSON 데이터가 올바르게 저장되고 조회된다") {
                    foundTest.shouldNotBeNull()
                    foundTest.json shouldBe complexJson
                    foundTest.json.size shouldBe 3
                }
            }
        }
    }
}