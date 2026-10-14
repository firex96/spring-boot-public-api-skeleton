package com.rex.api.pub.skeleton.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.ErrorCode
import org.springframework.validation.BindingResult
import java.util.stream.Collectors

/**
 * Global Exception Handler에서 발생한 에러에 대한 응답 처리를 관리
 */
data class ErrorResponseModel(
    @field:JsonProperty("code")
    val code: ErrorCode,
    @field:JsonProperty("reason")
    val reason: String? = null,
    @field:JsonProperty("errors")
    val errors: List<FieldError>? = null,
) {
    @field:JsonProperty("status")
    val status: Int = code.status

    @field:JsonProperty("divisionCode")
    val divisionCode: String = code.divisionCode

    @field:JsonProperty("resultMessage")
    val resultMessage: String = code.message

    // 에러를 e.getBindingResult() 형태로 전달 받는 경우 해당 내용을 상세 내용으로 변경하는 기능을 수행한다.
    data class FieldError(
        @field:JsonProperty("field")
        val field: String,
        @field:JsonProperty("value")
        val value: String,
        @field:JsonProperty("reason")
        val reason: String?,
    ) {
        companion object {
            fun of(
                field: String,
                value: String,
                reason: String?,
            ): List<FieldError> {
                val fieldErrors: MutableList<FieldError> = ArrayList()
                fieldErrors.add(FieldError(field, value, reason))
                return fieldErrors
            }

            internal fun of(bindingResult: BindingResult): List<FieldError> {
                val fieldErrors = bindingResult.fieldErrors

                return fieldErrors.stream().map { error: org.springframework.validation.FieldError ->
                    FieldError(
                        error.field,
                        if (error.rejectedValue == null) {
                            ""
                        } else {
                            error.rejectedValue.toString()
                        },
                        error.defaultMessage,
                    )
                }.collect(Collectors.toList())
            }
        }
    }

    companion object {
        fun of(
            code: ErrorCode,
            bindingResult: BindingResult,
        ): ErrorResponseModel = ErrorResponseModel(
            code = code,
            errors = FieldError.of(bindingResult),
        )

        fun of(code: ErrorCode): ErrorResponseModel = ErrorResponseModel(
            code = code,
        )

        fun of(
            code: ErrorCode,
            reason: String?,
        ): ErrorResponseModel = ErrorResponseModel(
            code = code,
            reason = reason,
        )
    }
}