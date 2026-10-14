package com.rex.api.pub.skeleton.client.example

import com.rex.api.pub.skeleton.dto.example.ExternalUserDataDto
import com.rex.api.pub.skeleton.dto.example.ExternalUserResponseDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.exception.ApiCallException
import com.rex.api.pub.skeleton.mapper.example.ExampleProxyMapper
import com.rex.api.pub.skeleton.util.WebClientUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.mapstruct.factory.Mappers
import org.springframework.http.HttpStatus

class ExampleWebClientTest : BehaviorSpec({
    val webClientUtil = mockk<WebClientUtil>()
    val exampleProxyMapper = Mappers.getMapper(ExampleProxyMapper::class.java)
    val service =
        ExampleWebClient(
            webClientUtil = webClientUtil,
            exampleProxyMapper = exampleProxyMapper,
            baseUrl = "http://test-api.example.com",
            maxRetryCount = 3,
            retryWaitSeconds = 1,
        )

    Given("사용자 등록 요청") {
        val request =
            InternalUserRequestDto(
                userId = "user123",
                userName = "홍길동",
                email = "hong@example.com",
                phone = "010-1234-5678",
            )

        When("외부 API가 성공 응답을 반환하면") {
            val externalResponse =
                ExternalUserResponseDto(
                    resultCode = "0000",
                    resultMessage = "등록 성공",
                    userData =
                    ExternalUserDataDto(
                        userId = "user123",
                        registrationDate = "2024-01-15T10:30:00",
                        statusCode = "A",
                    ),
                )

            every {
                webClientUtil.get(
                    baseUrl = any(),
                    uri = any(),
                    responseType = ExternalUserResponseDto::class.java,
                    timeout = any(),
                )
            } returns externalResponse

            val result = service.registerUser(request)

            Then("내부 포맷으로 변환된 성공 응답을 반환") {
                result.success shouldBe true
                result.message shouldBe "등록 성공"
                result.user?.userId shouldBe "user123"
            }

            Then("외부 API가 호출됨") {
                verify {
                    webClientUtil.get(
                        baseUrl = "http://test-api.example.com",
                        uri = "/api/v1/users/register",
                        responseType = ExternalUserResponseDto::class.java,
                        timeout = any(),
                    )
                }
            }
        }

        When("외부 API가 비즈니스 실패 응답을 반환하면") {
            val externalResponse =
                ExternalUserResponseDto(
                    resultCode = "E001",
                    resultMessage = "사용자 이미 존재",
                    userData = null,
                )

            every {
                webClientUtil.get(
                    baseUrl = any(),
                    uri = any(),
                    responseType = ExternalUserResponseDto::class.java,
                    timeout = any(),
                )
            } returns externalResponse

            val result = service.registerUser(request)

            Then("내부 포맷으로 변환된 실패 응답을 반환") {
                result.success shouldBe false
                result.message shouldBe "사용자 이미 존재"
                result.user shouldBe null
            }
        }

        When("외부 API가 null을 반환하면") {
            every {
                webClientUtil.get(
                    baseUrl = any(),
                    uri = any(),
                    responseType = ExternalUserResponseDto::class.java,
                    timeout = any(),
                )
            } returns null

            Then("ApiCallException 발생") {
                val exception =
                    shouldThrow<ApiCallException> {
                        service.registerUser(request)
                    }
                exception.httpStatus shouldBe HttpStatus.BAD_GATEWAY
            }
        }

        When("외부 API 호출 중 예외 발생") {
            every {
                webClientUtil.get(
                    baseUrl = any(),
                    uri = any(),
                    responseType = ExternalUserResponseDto::class.java,
                    timeout = any(),
                )
            } throws RuntimeException("Connection timeout")

            Then("ApiCallException으로 래핑되어 발생") {
                val exception =
                    shouldThrow<ApiCallException> {
                        service.registerUser(request)
                    }
                exception.httpStatus shouldBe HttpStatus.BAD_GATEWAY
                exception.message shouldBe "외부 API 호출 실패: Connection timeout"
            }
        }
    }
})