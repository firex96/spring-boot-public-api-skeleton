package com.rex.api.pub.skeleton.enums

/**
 * StringEnum 유틸리티 함수
 */
object StringEnumUtil {
    inline fun <reified T> fromValue(value: String): T where T : Enum<T>, T : StringEnum =
        enumValues<T>().firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("Unknown value[$value] for enum type ${T::class.simpleName}")
}