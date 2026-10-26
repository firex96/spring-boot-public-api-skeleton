package com.rex.api.pub.skeleton.enums

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * `ErrorCode` 의 status 는 그대로 HTTP 응답 상태가 된다(`GlobalExceptionHandler` 가 이 값으로만 상태를 정한다).
 * 그래서 오류 코드는 4xx·5xx 여야 하고, 소비자가 분기에 쓰는 구분 코드는 겹치면 안 된다.
 * message 는 응답 `resultMessage` 로 나가므로 사람이 읽는 제목이어야 한다.
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

        // message 는 응답 resultMessage 로 그대로 나간다 — 예외 클래스 이름·패키지 표기·한글 설명문이 아니라 사람이 읽는 제목이어야 한다
        ErrorCode.entries.forEach { errorCode ->
            it("${errorCode.name} 의 message 는 사용자에게 보여도 되는 제목이다") {
                errorCode.message shouldNotContain "Exception"
                errorCode.message shouldNotContain Regex("[a-z]+\\.[a-z]+")
                errorCode.message shouldNotContain Regex("[가-힣]")
                errorCode.message.trim() shouldBe errorCode.message
            }
        }
    }
})