package com.rex.api.pub.skeleton.enums

import com.rex.api.pub.skeleton.handler.ErrorReason
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** 접근 거부 짝 — 인증 필요는 401, 권한 없음은 403 이고, 문장은 상태 기반 처리(`ErrorReason.forStatus`)의 같은 상태 문장과 같다. */
class AccessDenialTest : DescribeSpec({

    describe("AccessDenial") {
        it("AUTHENTICATION_REQUIRED 는 401 UNAUTHORIZED_ERROR 다") {
            AccessDenial.AUTHENTICATION_REQUIRED.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
            AccessDenial.AUTHENTICATION_REQUIRED.errorCode.status shouldBe 401
            AccessDenial.AUTHENTICATION_REQUIRED.reason shouldBe ErrorReason.forStatus(401)
        }

        it("ACCESS_FORBIDDEN 은 403 FORBIDDEN_ERROR 다") {
            AccessDenial.ACCESS_FORBIDDEN.errorCode shouldBe ErrorCode.FORBIDDEN_ERROR
            AccessDenial.ACCESS_FORBIDDEN.errorCode.status shouldBe 403
            AccessDenial.ACCESS_FORBIDDEN.reason shouldBe ErrorReason.forStatus(403)
        }
    }
})