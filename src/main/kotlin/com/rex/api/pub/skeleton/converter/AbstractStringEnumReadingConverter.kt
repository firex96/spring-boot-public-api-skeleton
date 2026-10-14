package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.StringEnum
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter

/**
 * String -> StringEnum 변환 추상 클래스 (JDBC DB 조회용).
 *
 * @param fromValue String -> Enum 변환 함수
 */
@ReadingConverter
abstract class AbstractStringEnumReadingConverter<T>(
    private val fromValue: (String) -> T,
) : Converter<String, T> where T : Enum<T>, T : StringEnum {
    override fun convert(source: String): T = fromValue(source)
}