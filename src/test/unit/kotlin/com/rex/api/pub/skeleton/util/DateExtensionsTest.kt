package com.rex.api.pub.skeleton.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class DateExtensionsTest : DescribeSpec({

    describe("LocalDate.inPeriod") {
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 12, 31)

        context("기간 내 날짜") {
            it("시작일과 같으면 true") {
                startDate.inPeriod(startDate, endDate) shouldBe true
            }

            it("종료일과 같으면 true") {
                endDate.inPeriod(startDate, endDate) shouldBe true
            }

            it("시작일과 종료일 사이면 true") {
                LocalDate.of(2024, 6, 15).inPeriod(startDate, endDate) shouldBe true
            }

            it("시작일 다음날이면 true") {
                LocalDate.of(2024, 1, 2).inPeriod(startDate, endDate) shouldBe true
            }

            it("종료일 전날이면 true") {
                LocalDate.of(2024, 12, 30).inPeriod(startDate, endDate) shouldBe true
            }
        }

        context("기간 외 날짜") {
            it("시작일 이전이면 false") {
                LocalDate.of(2023, 12, 31).inPeriod(startDate, endDate) shouldBe false
            }

            it("종료일 이후면 false") {
                LocalDate.of(2025, 1, 1).inPeriod(startDate, endDate) shouldBe false
            }

            it("훨씬 이전이면 false") {
                LocalDate.of(2020, 1, 1).inPeriod(startDate, endDate) shouldBe false
            }

            it("훨씬 이후면 false") {
                LocalDate.of(2030, 1, 1).inPeriod(startDate, endDate) shouldBe false
            }
        }

        context("동일한 시작일과 종료일") {
            val sameDate = LocalDate.of(2024, 6, 15)

            it("같은 날짜면 true") {
                sameDate.inPeriod(sameDate, sameDate) shouldBe true
            }

            it("하루 전이면 false") {
                LocalDate.of(2024, 6, 14).inPeriod(sameDate, sameDate) shouldBe false
            }

            it("하루 후면 false") {
                LocalDate.of(2024, 6, 16).inPeriod(sameDate, sameDate) shouldBe false
            }
        }

        context("하루 기간") {
            val dayStart = LocalDate.of(2024, 6, 15)
            val dayEnd = LocalDate.of(2024, 6, 16)

            it("시작일이면 true") {
                dayStart.inPeriod(dayStart, dayEnd) shouldBe true
            }

            it("종료일이면 true") {
                dayEnd.inPeriod(dayStart, dayEnd) shouldBe true
            }

            it("이전이면 false") {
                LocalDate.of(2024, 6, 14).inPeriod(dayStart, dayEnd) shouldBe false
            }

            it("이후면 false") {
                LocalDate.of(2024, 6, 17).inPeriod(dayStart, dayEnd) shouldBe false
            }
        }
    }
})