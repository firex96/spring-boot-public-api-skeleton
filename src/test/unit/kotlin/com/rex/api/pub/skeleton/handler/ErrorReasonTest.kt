package com.rex.api.pub.skeleton.handler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** 상태 기반 문장 선택 — 4xx 는 상태별 문장, 5xx 는 게이트웨이 계열(502~504)만 외부 서비스 문장. */
class ErrorReasonTest : DescribeSpec({

    describe("forStatus") {
        mapOf(
            401 to ErrorReason.AUTHENTICATION_REQUIRED,
            403 to ErrorReason.ACCESS_FORBIDDEN,
            404 to ErrorReason.PATH_NOT_FOUND,
            409 to ErrorReason.CONFLICT,
            423 to ErrorReason.LOCKED,
            405 to ErrorReason.METHOD_NOT_ALLOWED,
            406 to ErrorReason.NOT_ACCEPTABLE,
            413 to ErrorReason.PAYLOAD_TOO_LARGE,
            415 to ErrorReason.UNSUPPORTED_MEDIA_TYPE,
            400 to ErrorReason.BAD_REQUEST,
            418 to ErrorReason.BAD_REQUEST,
        ).forEach { (status, reason) ->
            it("$status 의 문장은 「$reason」 다") {
                ErrorReason.forStatus(status) shouldBe reason
            }
        }
    }

    describe("forServerError") {
        it("500 은 일반 문장이다") {
            ErrorReason.forServerError(500) shouldBe ErrorReason.SERVER_ERROR
        }

        listOf(502, 503, 504).forEach { status ->
            it("$status 은 외부 서비스 문장이다") {
                ErrorReason.forServerError(status) shouldBe ErrorReason.EXTERNAL_SERVICE_ERROR
            }
        }
    }
})