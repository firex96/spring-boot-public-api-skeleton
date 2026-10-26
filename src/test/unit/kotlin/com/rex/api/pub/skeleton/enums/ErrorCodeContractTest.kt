package com.rex.api.pub.skeleton.enums

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.ints.shouldBeInRange

/**
 * `ErrorCode` 의 status 는 그대로 HTTP 응답 상태가 된다(`GlobalExceptionHandler` 가 이 값으로만 상태를 정한다).
 * 그래서 오류 코드는 4xx·5xx 여야 하고, 소비자가 분기에 쓰는 구분 코드는 겹치면 안 된다.
 */
class ErrorCodeContractTest : DescribeSpec({

    describe("ErrorCode") {
        ErrorCode.entries.forEach { errorCode ->
            it("${errorCode.name} 의 status 는 오류 상태(400~599)다") {
                errorCode.status shouldBeInRange 400..599
            }
        }

        it("divisionCode 은 서로 다르다") {
            ErrorCode.entries.map { it.divisionCode }.shouldBeUnique()
        }
    }
})