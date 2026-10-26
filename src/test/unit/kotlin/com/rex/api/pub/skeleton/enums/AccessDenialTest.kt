package com.rex.api.pub.skeleton.enums

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** 접근 거부 짝 — 인증 필요는 401, 권한 없음은 403 이다. */
class AccessDenialTest : DescribeSpec({

    describe("AccessDenial") {
        it("AUTHENTICATION_REQUIRED 는 401 UNAUTHORIZED_ERROR 다") {
            AccessDenial.AUTHENTICATION_REQUIRED.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
            AccessDenial.AUTHENTICATION_REQUIRED.errorCode.status shouldBe 401
        }

        it("ACCESS_FORBIDDEN 은 403 FORBIDDEN_ERROR 다") {
            AccessDenial.ACCESS_FORBIDDEN.errorCode shouldBe ErrorCode.FORBIDDEN_ERROR
            AccessDenial.ACCESS_FORBIDDEN.errorCode.status shouldBe 403
        }
    }
})