package com.rex.api.pub.skeleton.enums

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * `ErrorCode.fromStatus` — 상태 기반 응답의 코드 선택. 기대 표 전체로 최종 매핑을 확인하고(목록 중복은 `BY_STATUS` 초기화의 `check` 가 거부한다),
 * 목록 밖 상태는 4xx 400 코드·5xx 500 코드로 낮춘다.
 */
class ErrorCodeFromStatusTest : DescribeSpec({

    describe("fromStatus") {
        val expected =
            mapOf(
                400 to ErrorCode.BAD_REQUEST_ERROR,
                401 to ErrorCode.UNAUTHORIZED_ERROR,
                403 to ErrorCode.FORBIDDEN_ERROR,
                404 to ErrorCode.NOT_FOUND_ERROR,
                405 to ErrorCode.METHOD_NOT_ALLOWED_ERROR,
                406 to ErrorCode.NOT_ACCEPTABLE_ERROR,
                409 to ErrorCode.CONFLICT_ERROR,
                413 to ErrorCode.PAYLOAD_TOO_LARGE_ERROR,
                415 to ErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR,
                423 to ErrorCode.ACCOUNT_LOCKED_ERROR,
                500 to ErrorCode.INTERNAL_SERVER_ERROR,
                502 to ErrorCode.EXTERNAL_API_ERROR,
                503 to ErrorCode.SERVICE_UNAVAILABLE_ERROR,
            )

        expected.forEach { (status, errorCode) ->
            it("$status 은 ${errorCode.name} 다") {
                ErrorCode.fromStatus(status) shouldBe errorCode
                ErrorCode.fromStatus(status).status shouldBe status
            }
        }

        it("목록 밖 4xx(418)는 BAD_REQUEST_ERROR 다") {
            ErrorCode.fromStatus(418) shouldBe ErrorCode.BAD_REQUEST_ERROR
        }

        it("목록 밖 5xx(504)는 INTERNAL_SERVER_ERROR 다") {
            ErrorCode.fromStatus(504) shouldBe ErrorCode.INTERNAL_SERVER_ERROR
        }
    }
})