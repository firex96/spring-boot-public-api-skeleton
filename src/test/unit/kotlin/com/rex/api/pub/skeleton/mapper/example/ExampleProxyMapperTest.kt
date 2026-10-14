package com.rex.api.pub.skeleton.mapper.example

import com.rex.api.pub.skeleton.dto.example.ExternalUserDataDto
import com.rex.api.pub.skeleton.dto.example.ExternalUserResponseDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.enums.example.UserStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime

class ExampleProxyMapperTest : DescribeSpec({
    val mapper = Mappers.getMapper(ExampleProxyMapper::class.java)

    describe("ExampleProxyMapper.toExternalRequest") {
        context("내부 요청을 외부 포맷으로 변환") {
            it("모든 필드가 올바르게 매핑됨") {
                val request =
                    InternalUserRequestDto(
                        userId = "user123",
                        userName = "홍길동",
                        email = "hong@example.com",
                        phone = "010-1234-5678",
                    )

                val result = mapper.toExternalRequest(request)

                result.userId shouldBe "user123"
                result.userName shouldBe "홍길동"
                result.emailAddress shouldBe "hong@example.com"
                result.phoneNumber shouldBe "01012345678" // 하이픈 제거됨
                result.requestTimestamp.shouldNotBeNull()
            }

            it("null 필드도 올바르게 처리됨") {
                val request =
                    InternalUserRequestDto(
                        userId = "user456",
                        userName = "김철수",
                        email = null,
                        phone = null,
                    )

                val result = mapper.toExternalRequest(request)

                result.userId shouldBe "user456"
                result.emailAddress.shouldBeNull()
                result.phoneNumber.shouldBeNull()
            }

            it("전화번호에서 특수문자가 제거됨") {
                val request =
                    InternalUserRequestDto(
                        userId = "user789",
                        userName = "이영희",
                        email = null,
                        phone = "02-123-4567",
                    )

                val result = mapper.toExternalRequest(request)

                result.phoneNumber shouldBe "021234567"
            }
        }
    }

    describe("ExampleProxyMapper.toInternalResponse") {
        context("성공 응답 변환") {
            it("성공 코드(0000)이면 success=true") {
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

                val result = mapper.toInternalResponse(externalResponse)

                result.success shouldBe true
                result.message shouldBe "등록 성공"
                result.user.shouldNotBeNull()
                result.user!!.userId shouldBe "user123"
                result.user!!.status shouldBe UserStatus.ACTIVE
                result.user!!.registeredAt shouldBe LocalDateTime.of(2024, 1, 15, 10, 30, 0)
            }
        }

        context("실패 응답 변환") {
            it("성공 코드가 아니면 success=false") {
                val externalResponse =
                    ExternalUserResponseDto(
                        resultCode = "E001",
                        resultMessage = "사용자 없음",
                        userData = null,
                    )

                val result = mapper.toInternalResponse(externalResponse)

                result.success shouldBe false
                result.message shouldBe "사용자 없음"
                result.user.shouldBeNull()
            }
        }

        context("사용자 상태 코드 변환") {
            it("A -> ACTIVE") {
                val response =
                    ExternalUserResponseDto(
                        resultCode = "0000",
                        resultMessage = "성공",
                        userData = ExternalUserDataDto("u1", null, "A"),
                    )

                mapper.toInternalResponse(response).user?.status shouldBe UserStatus.ACTIVE
            }

            it("I -> INACTIVE") {
                val response =
                    ExternalUserResponseDto(
                        resultCode = "0000",
                        resultMessage = "성공",
                        userData = ExternalUserDataDto("u1", null, "I"),
                    )

                mapper.toInternalResponse(response).user?.status shouldBe UserStatus.INACTIVE
            }

            it("P -> PENDING") {
                val response =
                    ExternalUserResponseDto(
                        resultCode = "0000",
                        resultMessage = "성공",
                        userData = ExternalUserDataDto("u1", null, "P"),
                    )

                mapper.toInternalResponse(response).user?.status shouldBe UserStatus.PENDING
            }

            it("알 수 없는 코드 -> UNKNOWN") {
                val response =
                    ExternalUserResponseDto(
                        resultCode = "0000",
                        resultMessage = "성공",
                        userData = ExternalUserDataDto("u1", null, "X"),
                    )

                mapper.toInternalResponse(response).user?.status shouldBe UserStatus.UNKNOWN
            }
        }

        context("날짜 파싱") {
            it("유효한 날짜 문자열이 LocalDateTime으로 변환됨") {
                val response =
                    ExternalUserResponseDto(
                        resultCode = "0000",
                        resultMessage = "성공",
                        userData = ExternalUserDataDto("u1", "2024-12-25T15:30:45", "A"),
                    )

                val result = mapper.toInternalResponse(response)

                result.user?.registeredAt shouldBe LocalDateTime.of(2024, 12, 25, 15, 30, 45)
            }

            it("잘못된 날짜 포맷은 null 반환") {
                val response =
                    ExternalUserResponseDto(
                        resultCode = "0000",
                        resultMessage = "성공",
                        userData = ExternalUserDataDto("u1", "invalid-date", "A"),
                    )

                val result = mapper.toInternalResponse(response)

                result.user?.registeredAt.shouldBeNull()
            }

            it("null 날짜는 null 반환") {
                val response =
                    ExternalUserResponseDto(
                        resultCode = "0000",
                        resultMessage = "성공",
                        userData = ExternalUserDataDto("u1", null, "A"),
                    )

                val result = mapper.toInternalResponse(response)

                result.user?.registeredAt.shouldBeNull()
            }
        }
    }

    describe("ExampleProxyMapper.toErrorResponse") {
        context("에러 코드별 메시지 변환") {
            it("E001 -> 사용자를 찾을 수 없습니다") {
                val result = mapper.toErrorResponse("E001", "User not found")

                result.success shouldBe false
                result.message shouldBe "사용자를 찾을 수 없습니다."
            }

            it("E002 -> 잘못된 요청 형식입니다") {
                val result = mapper.toErrorResponse("E002", "Invalid format")

                result.message shouldBe "잘못된 요청 형식입니다."
            }

            it("E003 -> 인증에 실패했습니다") {
                val result = mapper.toErrorResponse("E003", "Auth failed")

                result.message shouldBe "인증에 실패했습니다."
            }

            it("E004 -> 권한이 없습니다") {
                val result = mapper.toErrorResponse("E004", "No permission")

                result.message shouldBe "권한이 없습니다."
            }

            it("E005 -> 요청 한도를 초과했습니다") {
                val result = mapper.toErrorResponse("E005", "Rate limit")

                result.message shouldBe "요청 한도를 초과했습니다."
            }

            it("알 수 없는 코드 -> 원본 메시지 포함") {
                val result = mapper.toErrorResponse("E999", "Unknown error occurred")

                result.message shouldBe "외부 시스템 오류: Unknown error occurred"
            }
        }
    }
})