package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.StringEnum
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * 빈 문자열을 null로 처리하는 StringEnum Converter.
 *
 * DB에 빈 문자열("")이 저장된 경우 null로 변환합니다.
 */
@Converter
abstract class AbstractBlankStringEnumConverter<T>(
    private val fromValue: (String) -> T?,
) : AttributeConverter<T, String> where T : Enum<T>, T : StringEnum {
    override fun convertToDatabaseColumn(attribute: T?): String? = attribute?.value

    override fun convertToEntityAttribute(dbData: String?): T? =
        if (dbData.isNullOrBlank()) null else fromValue(dbData)
}