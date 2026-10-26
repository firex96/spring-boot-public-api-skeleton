package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.enums.ErrorCode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ExceptionTest : DescribeSpec({

    describe("CodedException") {
        it("메시지를 생략하면 errorCode 의 메시지를 쓴다") {
            val exception = CodedException(ErrorCode.CONFLICT_ERROR)

            exception.errorCode shouldBe ErrorCode.CONFLICT_ERROR
            exception.message shouldBe ErrorCode.CONFLICT_ERROR.message
        }

        it("메시지와 cause 를 보존한다") {
            val cause = RuntimeException("Original error")
            val exception = CodedException(ErrorCode.CONFLICT_ERROR, "이미 처리된 요청입니다", cause)

            exception.message shouldBe "이미 처리된 요청입니다"
            exception.cause shouldBe cause
        }
    }

    describe("ItemNotFoundException") {
        it("NOT_FOUND_ERROR 를 싣는다") {
            val exception = ItemNotFoundException("Item not found")

            exception.errorCode shouldBe ErrorCode.NOT_FOUND_ERROR
            exception.message shouldBe "Item not found"
        }
    }

    describe("ClientBadRequestException") {
        it("BAD_REQUEST_ERROR 를 싣는다") {
            val exception = ClientBadRequestException("Bad request")

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST_ERROR
            exception.message shouldBe "Bad request"
        }
    }

    describe("InvalidRequestParameterException") {
        it("기본 메시지와 BAD_REQUEST_ERROR 를 싣는다") {
            val exception = InvalidRequestParameterException()

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST_ERROR
            exception.message shouldBe "유효하지 않은 요청 파라미터 입니다."
        }

        it("지정한 메시지를 쓴다") {
            val exception = InvalidRequestParameterException("Custom error message")

            exception.message shouldBe "Custom error message"
        }

        it("ClientBadRequestException 의 하위 타입이다") {
            val exception = InvalidRequestParameterException()
            exception.shouldBeInstanceOf<ClientBadRequestException>()
        }
    }

    describe("ApiCallException") {
        it("기본은 502 EXTERNAL_API_ERROR 다") {
            val exception = ApiCallException()

            exception.message shouldBe "외부 API 호출에 실패했습니다."
            exception.errorCode shouldBe ErrorCode.EXTERNAL_API_ERROR
        }

        it("지정한 메시지를 쓴다") {
            val exception = ApiCallException("Custom API error")

            exception.message shouldBe "Custom API error"
            exception.errorCode shouldBe ErrorCode.EXTERNAL_API_ERROR
        }

        it("코드·메시지·cause 를 보존한다") {
            val cause = RuntimeException("Original error")
            val exception = ApiCallException("Service unavailable", ErrorCode.SERVICE_UNAVAILABLE_ERROR, cause)

            exception.message shouldBe "Service unavailable"
            exception.errorCode shouldBe ErrorCode.SERVICE_UNAVAILABLE_ERROR
            exception.cause shouldBe cause
        }
    }
})