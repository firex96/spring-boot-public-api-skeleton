package com.rex.api.pub.skeleton.persistence.jpa.service.main

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.dto.ProductDto
import com.rex.api.pub.skeleton.enums.ProductStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * ProductJpaEntityService 통합 테스트.
 *
 * Testcontainers를 통해 MySQL 데이터베이스와 연동하여 테스트합니다.
 * - 실제 JPA Repository 사용
 * - 실제 MapStruct Mapper 사용
 * - 트랜잭션 롤백으로 테스트 격리
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.persistence.jpa.service.main.ProductJpaEntityServiceIntegrationTest"
 * ```
 */
@SpringBootTest
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
@Transactional
class ProductJpaEntityServiceIntegrationTest(
    private val service: ProductJpaEntityService,
) : BehaviorSpec() {

    init {
        Given("Product 생성을 위한 ProductDto") {
            val productDto = createProductDto(id = 0L, categoryId = 100)

            When("create 메서드를 호출하면") {
                val createdProduct = service.create(productDto)

                Then("ID가 생성된 ProductDto가 반환된다") {
                    createdProduct.shouldNotBeNull()
                    createdProduct.id shouldBeGreaterThan 0L
                    createdProduct.name shouldBe productDto.name
                    createdProduct.status shouldBe productDto.status
                    createdProduct.categoryId shouldBe productDto.categoryId
                }
            }
        }

        Given("ID가 0이 아닌 ProductDto") {
            val productDto = createProductDto(id = 123, categoryId = 100)

            When("create 메서드를 호출하면") {
                Then("IllegalArgumentException이 발생한다") {
                    val exception = shouldThrow<IllegalArgumentException> {
                        service.create(productDto)
                    }
                    exception.message shouldBe "Product ID must be zero for new product creation"
                }
            }
        }

        Given("저장된 Product") {
            val productDto = createProductDto(id = 0L, categoryId = 200)
            val savedProduct = service.create(productDto)

            When("get 메서드로 ID로 조회하면") {
                val foundProduct = service.get(savedProduct.id)

                Then("저장된 Product가 조회된다") {
                    foundProduct.shouldNotBeNull()
                    foundProduct.id shouldBe savedProduct.id
                    foundProduct.name shouldBe savedProduct.name
                }
            }

            When("존재하지 않는 ID로 조회하면") {
                val foundProduct = service.get(999999)

                Then("null이 반환된다") {
                    foundProduct.shouldBeNull()
                }
            }
        }

        Given("동일 categoryId를 가진 여러 Product") {
            // 기존 데이터와 충돌하지 않도록 매우 큰 categoryId 사용
            val uniqueCategoryId = 9999000L + (System.currentTimeMillis() % 1000)
            val product1 = service.create(createProductDto(id = 0L, categoryId = uniqueCategoryId, name = "Kim"))
            val product2 = service.create(createProductDto(id = 0L, categoryId = uniqueCategoryId, name = "Lee"))
            service.create(createProductDto(id = 0L, categoryId = uniqueCategoryId + 1, name = "Park")) // 다른 categoryId

            When("getAllByCategoryId로 조회하면") {
                val productList = service.getAllByCategoryId(uniqueCategoryId)

                Then("해당 categoryId의 Product만 조회된다") {
                    productList.size shouldBeGreaterThan 1
                    productList.all { it.categoryId == uniqueCategoryId } shouldBe true
                    productList.map { it.id } shouldContainAll listOf(product1.id, product2.id)
                }
            }

            When("Product가 없는 categoryId로 조회하면") {
                val nonExistentCategoryId = 8888888L
                val productList = service.getAllByCategoryId(nonExistentCategoryId)

                Then("빈 리스트가 반환된다") {
                    productList shouldHaveSize 0
                }
            }
        }

        Given("수정할 Product") {
            val productDto = createProductDto(id = 0L, categoryId = 500)
            val savedProduct = service.create(productDto)

            When("update 메서드로 수정하면") {
                val updatedDto = savedProduct.copy(
                    name = "UpdatedName",
                    status = ProductStatus.DISCONTINUED,
                )
                val result = service.update(updatedDto)

                Then("수정된 Product가 반환된다") {
                    result.shouldNotBeNull()
                    result.id shouldBe savedProduct.id
                    result.name shouldBe "UpdatedName"
                    result.status shouldBe ProductStatus.DISCONTINUED
                }

                And("조회 시 수정된 값이 확인된다") {
                    val foundProduct = service.get(savedProduct.id)
                    foundProduct.shouldNotBeNull()
                    foundProduct.name shouldBe "UpdatedName"
                    foundProduct.status shouldBe ProductStatus.DISCONTINUED
                }
            }
        }

        Given("존재하지 않는 Product ID로 update 시도") {
            val nonExistentDto = createProductDto(id = 999999, categoryId = 100)

            When("update 메서드를 호출하면") {
                Then("NoSuchElementException이 발생한다") {
                    val exception = shouldThrow<NoSuchElementException> {
                        service.update(nonExistentDto)
                    }
                    exception.message shouldBe "Product with id ${nonExistentDto.id} not found"
                }
            }
        }

        Given("삭제할 Product") {
            val productDto = createProductDto(id = 0L, categoryId = 600)
            val savedProduct = service.create(productDto)

            When("delete 메서드로 삭제하면") {
                service.delete(savedProduct.id)

                Then("조회 시 null이 반환된다") {
                    val foundProduct = service.get(savedProduct.id)
                    foundProduct.shouldBeNull()
                }
            }
        }
    }
}

/**
 * 테스트용 ProductDto 생성 헬퍼 함수.
 */
private fun createProductDto(
    id: Long,
    categoryId: Long,
    name: String = "TestProduct",
    status: ProductStatus = ProductStatus.ON_SALE,
): ProductDto =
    ProductDto(
        id = id,
        status = status,
        name = name,
        description = "Test description",
        price = 10_000L,
        categoryId = categoryId,
        createdBy = 1,
        createdAt = LocalDateTime.now(),
        updatedBy = 1,
        updatedAt = LocalDateTime.now(),
    )