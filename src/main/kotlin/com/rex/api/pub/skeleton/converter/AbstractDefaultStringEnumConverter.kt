package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.StringEnum
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * 잘못된 값에 대해 기본값을 반환하는 StringEnum Converter.
 *
 * DB에 유효하지 않은 값이 저장된 경우 지정된 기본값으로 변환합니다.
 *
 * @param defaultValue 기본값 (null 가능)
 * @param fromValue String -> Enum 변환 함수 (찾지 못하면 null 반환)
 */
@Converter
abstract class AbstractDefaultStringEnumConverter<T>(
    private val defaultValue: T?,
    private val fromValue: (String) -> T?,
) : AttributeConverter<T, String> where T : Enum<T>, T : StringEnum {
    override fun convertToDatabaseColumn(attribute: T?): String? = attribute?.value

    override fun convertToEntityAttribute(dbData: String?): T? =
        if (dbData.isNullOrBlank()) {
            defaultValue
        } else {
            fromValue(dbData) ?: defaultValue
        }
}