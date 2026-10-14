package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.StringEnum
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * StringEnum을 구현한 Enum 타입의 JPA AttributeConverter 기본 클래스.
 *
 * @param nullable null 허용 여부 (false인 경우 null -> "" 변환)
 * @param fromValue String -> Enum 변환 함수
 */
@Converter
abstract class AbstractStringEnumConverter<T>(
    private val nullable: Boolean = false,
    private val fromValue: (String) -> T,
) : AttributeConverter<T, String> where T : Enum<T>, T : StringEnum {
    override fun convertToDatabaseColumn(attribute: T?): String? =
        when {
            attribute == null && nullable -> null
            attribute == null -> ""
            else -> attribute.value
        }

    override fun convertToEntityAttribute(dbData: String?): T? = dbData?.let(fromValue)
}