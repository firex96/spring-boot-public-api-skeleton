package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.StringEnum
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * StringEnum -> String 변환 (JDBC DB 저장용).
 * 모든 StringEnum 타입에 공통으로 사용됩니다.
 */
@WritingConverter
class StringEnumWritingConverter : Converter<StringEnum, String> {
    override fun convert(source: StringEnum): String = source.value
}