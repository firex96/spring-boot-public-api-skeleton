package com.rex.api.pub.skeleton.exception

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class DefaultErrorMessageServiceTest : DescribeSpec({

    val service = DefaultErrorMessageService()

    describe("DefaultErrorMessageService") {

        describe("getMessage") {

            it("should return exception code when exception implements ExceptionCode") {
                val exception = ClientBadRequestException("ERR001", "Custom error message")

                val result = service.getMessage(exception, "DEFAULT")

                result.code shouldBe "ERR001"
                result.values shouldBe listOf("Custom error message")
            }

            it("should return default code when exception code is null") {
                val exception = ClientBadRequestException("Error without code")

                val result = service.getMessage(exception, "DEFAULT_CODE")

                result.code shouldBe "DEFAULT_CODE"
                result.values shouldBe listOf("Error without code")
            }

            it("should return default code for non-ExceptionCode exceptions") {
                val exception = RuntimeException("Runtime error")

                val result = service.getMessage(exception, "RUNTIME_ERR")

                result.code shouldBe "RUNTIME_ERR"
                result.values shouldBe listOf("Runtime error")
            }

            it("should return default message when exception message is null") {
                val exception = RuntimeException()

                val result = service.getMessage(exception, "NULL_MSG")

                result.code shouldBe "NULL_MSG"
                result.values shouldBe listOf("알 수 없는 오류가 발생했습니다.")
            }

            it("should handle ItemNotFoundException") {
                val exception = ItemNotFoundException("ITEM001", "Item not found in database")

                val result = service.getMessage(exception, "NOT_FOUND")

                result.code shouldBe "ITEM001"
                result.values shouldBe listOf("Item not found in database")
            }

            it("should handle InvalidRequestParameterException") {
                val exception = InvalidRequestParameterException("PARAM001", "Invalid parameter")

                val result = service.getMessage(exception, "BAD_REQUEST")

                result.code shouldBe "PARAM001"
                result.values shouldBe listOf("Invalid parameter")
            }
        }
    }
})