package com.rex.api.pub.skeleton.util

import java.time.LocalDate

/**
 * 날짜가 지정된 기간 내에 있는지 확인 (시작일, 종료일 포함).
 *
 * @param startDate 시작일
 * @param endDate 종료일
 * @return 기간 내에 있으면 true
 */
fun LocalDate.inPeriod(
    startDate: LocalDate,
    endDate: LocalDate,
): Boolean = this == startDate || this == endDate || (this.isAfter(startDate) && this.isBefore(endDate))