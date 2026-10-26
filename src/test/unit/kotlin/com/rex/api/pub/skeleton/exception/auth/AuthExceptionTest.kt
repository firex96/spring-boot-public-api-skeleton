package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.exception.CodedException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** 인증 예외는 자기 코드만 정한다 — 응답(상태·구분 코드)은 `CodedException` 핸들러가 이 코드로 만든다. */
class AuthExceptionTest : DescribeSpec({

    describe("인증 예외의 errorCode") {
        val cases: List<Pair<CodedException, ErrorCode>> =
            listOf(
                AuthenticationException("인증 실패") to ErrorCode.UNAUTHORIZED_ERROR,
                UnauthorizedException("인증 필요") to ErrorCode.UNAUTHORIZED_ERROR,
                InvalidTokenException("토큰 만료") to ErrorCode.UNAUTHORIZED_ERROR,
                ForbiddenException("권한 없음") to ErrorCode.FORBIDDEN_ERROR,
                AuthorizationException("그룹 관리 권한 없음") to ErrorCode.FORBIDDEN_ERROR,
                DuplicateUserException("이미 있는 사용자") to ErrorCode.CONFLICT_ERROR,
                DuplicateResourceException("이미 있는 리소스") to ErrorCode.CONFLICT_ERROR,
                ResourceNotFoundException("리소스 없음") to ErrorCode.NOT_FOUND_ERROR,
                AccountLockedException("계정 잠금") to ErrorCode.ACCOUNT_LOCKED_ERROR,
                MfaException(ErrorCode.MFA_TOO_MANY_ATTEMPTS, "시도 초과") to ErrorCode.MFA_TOO_MANY_ATTEMPTS,
            )

        cases.forEach { (exception, expected) ->
            it("${exception.javaClass.simpleName} → ${expected.name}") {
                exception.errorCode shouldBe expected
            }
        }

        it("메시지와 cause 를 보존한다") {
            val cause = IllegalStateException("원인")
            val exception = AuthenticationException("인증 실패", cause)

            exception.message shouldBe "인증 실패"
            exception.cause shouldBe cause
        }
    }
})