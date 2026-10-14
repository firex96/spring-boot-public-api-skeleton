package com.rex.api.pub.skeleton.mapper.example

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.example.ExternalUserDataDto
import com.rex.api.pub.skeleton.dto.example.ExternalUserRequestDto
import com.rex.api.pub.skeleton.dto.example.ExternalUserResponseDto
import com.rex.api.pub.skeleton.dto.example.InternalUserDataDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.dto.example.InternalUserResponseDto
import com.rex.api.pub.skeleton.enums.example.UserStatus
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 프록시 요청/응답 변환 매퍼.
 *
 * 내부 시스템 포맷과 외부 API 포맷 간의 변환을 담당합니다.
 *
 * 변환 시 고려사항:
 * - 필드명 매핑 (camelCase ↔ snake_case)
 * - 타입 변환 (LocalDateTime ↔ String, Enum ↔ String)
 * - 기본값 처리 (null 대체값)
 * - 데이터 검증 및 정제
 */
@Mapper(config = MapstructConfiguration::class)
interface ExampleProxyMapper {
    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private const val SUCCESS_CODE = "0000"

        private val ERROR_MESSAGE_MAP =
            mapOf(
                "E001" to "사용자를 찾을 수 없습니다.",
                "E002" to "잘못된 요청 형식입니다.",
                "E003" to "인증에 실패했습니다.",
                "E004" to "권한이 없습니다.",
                "E005" to "요청 한도를 초과했습니다.",
            )
    }

    /**
     * 내부 요청을 외부 API 요청 포맷으로 변환.
     */
    @Mapping(source = "email", target = "emailAddress")
    @Mapping(source = "phone", target = "phoneNumber", qualifiedByName = ["formatPhoneNumber"])
    @Mapping(target = "requestTimestamp", expression = "java(System.currentTimeMillis())")
    fun toExternalRequest(request: InternalUserRequestDto): ExternalUserRequestDto

    /**
     * 외부 API 응답을 내부 응답 포맷으로 변환.
     */
    @Mapping(target = "success", source = "resultCode", qualifiedByName = ["isSuccess"])
    @Mapping(target = "message", source = "resultMessage")
    @Mapping(target = "user", source = "userData")
    fun toInternalResponse(externalResponse: ExternalUserResponseDto): InternalUserResponseDto

    /**
     * 외부 사용자 데이터를 내부 포맷으로 변환.
     */
    @Mapping(target = "registeredAt", source = "registrationDate", qualifiedByName = ["parseDateTime"])
    @Mapping(target = "status", source = "statusCode", qualifiedByName = ["toUserStatus"])
    fun toInternalUserData(externalData: ExternalUserDataDto): InternalUserDataDto

    /**
     * 외부 API 에러를 내부 에러 응답으로 변환.
     */
    fun toErrorResponse(
        errorCode: String,
        errorMessage: String,
    ): InternalUserResponseDto {
        val translatedMessage = translateErrorMessage(errorCode, errorMessage)
        return InternalUserResponseDto(
            success = false,
            message = translatedMessage,
            user = null,
        )
    }

    /**
     * 에러 코드를 메시지로 변환.
     */
    fun translateErrorMessage(
        errorCode: String,
        originalMessage: String,
    ): String = ERROR_MESSAGE_MAP[errorCode] ?: "외부 시스템 오류: $originalMessage"

    /**
     * 전화번호에서 숫자만 추출.
     */
    @Named("formatPhoneNumber")
    fun formatPhoneNumber(phone: String?): String? = phone?.replace(Regex("[^0-9]"), "")

    /**
     * 결과 코드가 성공인지 확인.
     */
    @Named("isSuccess")
    fun isSuccess(resultCode: String?): Boolean = resultCode == SUCCESS_CODE

    /**
     * 날짜 문자열을 LocalDateTime으로 파싱.
     */
    @Named("parseDateTime")
    fun parseDateTime(dateString: String?): LocalDateTime? =
        dateString?.let {
            runCatching {
                LocalDateTime.parse(it, DATE_TIME_FORMATTER)
            }.getOrNull()
        }

    /**
     * 외부 상태 코드를 UserStatus로 변환.
     */
    @Named("toUserStatus")
    fun toUserStatus(statusCode: String?): UserStatus =
        statusCode?.let { UserStatus.fromExternalCode(it) } ?: UserStatus.UNKNOWN
}