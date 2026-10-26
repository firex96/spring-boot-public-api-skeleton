package com.rex.api.pub.skeleton.handler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** 5xx 문장은 상태로 고른다 — 게이트웨이 계열(502~504)만 외부 서비스 문장. */
class ErrorReasonTest : DescribeSpec({

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