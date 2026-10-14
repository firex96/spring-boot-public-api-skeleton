package com.rex.api.pub.skeleton.controller.product

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.controller.ProductRestController
import com.rex.api.pub.skeleton.model.ProductModel
import com.rex.api.pub.skeleton.service.mapping.CoreApiMappingService
import com.rex.api.pub.skeleton.service.mapping.ProductMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * ProductRestController 통합 테스트.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.product.ProductRestControllerTest"
 * ```
 *
 * 참고: 보안 필터 비활성화 (addFilters = false)하여 컨트롤러 로직만 테스트합니다.
 */
@WebMvcTest(controllers = [ProductRestController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class ProductRestControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var productMappingService: ProductMappingService

    @MockkBean
    lateinit var coreApiMappingService: CoreApiMappingService

    init {
        Given("Product REST Controller") {
            val fixtureMonkey = FixtureMonkey.builder().plugin(KotlinPlugin()).build()
            val productModel = fixtureMonkey.giveMeKotlinBuilder<ProductModel>().set(ProductModel::id, 2).sample()

            every { coreApiMappingService.getProduct(any()) } returns productModel

            When("Core API is called to get product details") {
                Then("Product details should be returned from Core API") {
                    mockMvc.get("/products/2/core") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.id") { value(2) }
                    }
                }
            }
        }
    }
}