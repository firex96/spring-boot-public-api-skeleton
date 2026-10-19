package com.rex.api.pub.skeleton.service.mfa

import com.rex.api.pub.skeleton.config.security.MfaProperties
import com.rex.api.pub.skeleton.dto.auth.LoginOutcomeDto
import com.rex.api.pub.skeleton.dto.auth.MfaChallengeDto
import com.rex.api.pub.skeleton.dto.auth.MfaCompletionDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.exception.auth.MfaException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.auth.LoginCompletionBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import com.rex.api.pub.skeleton.util.TokenHasher
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

/**
 * 2단계 challenge — 1단계를 통과한 2차 인증 대상의 **유일한** 대기 상태다(API·브라우저 공통). 세션은 2단계를 통과한 뒤에만 만든다.
 *
 * 1단계 출구는 모두 [afterFirstFactor] 를 지난다 — 2차 인증이 켜져 있으면 challenge, 아니면 바로 인증 완료(`LoginCompletionBizService`).
 * 새 로그인 경로도 이 관문을 지나면 2차 인증을 빠뜨리지 않는다.
 *
 * challenge 는 Redis 해시 `mfa:challenge:{sha256(token)}`(TTL [MfaProperties.challengeTtlSeconds])에 둔다. 원본 토큰은 호출자에게만 간다.
 * 완료는 존재 → 출처 → 차단·잠금 → 코드 → 소비(`DEL` 반환 1) 순서다 — 코드를 보기 전에 만료·출처·잠금을 끝내고,
 * 소비가 원자적이라 서로 다른 유효 코드를 동시에 내도 세션은 하나만 나간다.
 * 2단계 실패는 비밀번호 실패와 같은 계정 잠금 카운터를 올리고, 실패 카운터는 2단계 성공 때만 지운다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class MfaChallengeBizService(
    private val redisTemplate: StringRedisTemplate,
    private val mfaBizService: MfaBizService,
    private val loginCompletionBizService: LoginCompletionBizService,
    private val loginAttemptBizService: LoginAttemptBizService,
    private val userEntityService: UserEntityService,
    private val auditBizService: AuditBizService,
    private val tokenHasher: TokenHasher,
    private val mfaProperties: MfaProperties,
) {
    /**
     * 1단계 출구의 관문 — 2차 인증이 켜져 있으면 challenge 를 만들고, 아니면 인증을 끝내 세션을 발급한다.
     * 실패 카운터는 비밀번호 출처의 바로 완료에서만 지운다(소셜 1단계는 비밀번호 추측과 무관하고, 2차 인증 대상은 2단계 성공 때 지운다).
     * 활성 판정(공유 잠금)과 세션 발급이 한 쓰기 트랜잭션이라, 진행 중인 활성화가 이 세션을 놓치지 않는다.
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun afterFirstFactor(
        user: UserDto,
        origin: MfaOrigin,
        provider: SocialProvider?,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): LoginOutcomeDto =
        if (mfaBizService.isEnabledForLogin(user.id)) {
            LoginOutcomeDto.MfaRequired(issue(user, origin, provider, deviceType, deviceInfo))
        } else {
            LoginOutcomeDto.Tokens(loginCompletionBizService.complete(user, provider, deviceType, deviceInfo, ipAddress, clearAttempts = !origin.isSocial))
        }

    private fun issue(
        user: UserDto,
        origin: MfaOrigin,
        provider: SocialProvider?,
        deviceType: String?,
        deviceInfo: String?,
    ): MfaChallengeDto {
        val token = tokenHasher.generateToken()
        val fields =
            buildMap {
                put(FIELD_USER_ID, user.id.toString())
                put(FIELD_LOGIN_ID, user.loginId)
                put(FIELD_ORIGIN, origin.name)
                provider?.let { put(FIELD_PROVIDER, it.name) }
                deviceType?.let { put(FIELD_DEVICE_TYPE, it) }
                deviceInfo?.let { put(FIELD_DEVICE_INFO, it) }
            }
        // 필드와 만료를 한 스크립트로 — 둘 사이에 멈춰 TTL 없는 challenge 가 남지 않게
        redisTemplate.execute(
            SAVE_SCRIPT,
            listOf(KEY_PREFIX + token.hashedToken),
            mfaProperties.challengeTtlSeconds.toString(),
            *fields.flatMap { (k, v) -> listOf(k, v) }.toTypedArray(),
        )
        return MfaChallengeDto(token = token.rawToken, expiresIn = mfaProperties.challengeTtlSeconds, methods = mfaBizService.availableMethods(user.id))
    }

    /** challenge 가 살아 있고 [allowedOrigins] 의 것인가 — 화면을 보여 주기 전 확인용. 아무것도 바꾸지 않는다. */
    fun isPending(token: String, allowedOrigins: Set<MfaOrigin>): Boolean =
        redisTemplate.opsForHash<String, String>().get(key(token), FIELD_ORIGIN)?.let { MfaOrigin.valueOf(it) in allowedOrigins } ?: false

    @Transactional(
        transactionManager = "authTransactionManager",
        noRollbackFor = [MfaException::class, AccountLockedException::class, ForbiddenException::class],
    )
    fun complete(
        token: String,
        allowedOrigins: Set<MfaOrigin>,
        method: MfaMethod,
        code: String,
        ipAddress: String?,
    ): MfaCompletionDto {
        val key = key(token)
        // 1. 존재 — 만료·소비·한도 초과로 지워졌으면 무효
        val challenge = redisTemplate.opsForHash<String, String>().entries(key)
        if (challenge.isEmpty()) throw invalidChallenge()
        // 2. 출처 — 다른 완료 경로의 challenge 는 코드를 보지 않고 거부한다. 소비하지 않는다(정상 경로의 사용자가 계속 진행할 수 있게)
        val origin = MfaOrigin.valueOf(challenge.getValue(FIELD_ORIGIN))
        if (origin !in allowedOrigins) throw invalidChallenge()
        val userId = challenge.getValue(FIELD_USER_ID).toLong()
        val loginId = challenge.getValue(FIELD_LOGIN_ID)
        // 3. 차단 IP·잠긴 계정 — 코드를 보기 전에(다른 경로의 실패로 잠겼을 수 있다)
        if (ipAddress != null && loginAttemptBizService.isIpBlocked(ipAddress)) throw ForbiddenException("차단된 IP 입니다")
        if (loginAttemptBizService.isUserLocked(loginId)) throw loginAttemptBizService.accountLockedException(loginId)
        // 4. 코드
        if (!mfaBizService.verifyCode(userId, method, code)) {
            // challenge 가 그사이 사라졌으면(동시에 낸 다른 요청이 통과·만료) 실패로 세지 않는다 — 같은 코드를 동시에 낸 사용자가 잠기지 않게
            if (redisTemplate.execute(FAIL_SCRIPT, listOf(key), mfaProperties.maxChallengeFailures.toString()) == CHALLENGE_GONE) throw invalidChallenge()
            loginAttemptBizService.recordFailedAttempt(loginId, ipAddress)
            auditBizService.logSafely(
                eventType = AuditEventType.MFA_CHALLENGE_FAILURE,
                eventCategory = AuditBizService.CATEGORY_AUTH,
                userId = userId,
                username = loginId,
                ipAddress = ipAddress,
                description = "2단계 코드 불일치 (${origin.name})",
                result = AuditResult.FAILURE,
                failureReason = "INVALID_CODE",
            )
            // 이 실패(또는 동시에 들어온 다른 실패)로 잠겼으면 잠금으로 알린다
            if (loginAttemptBizService.isUserLocked(loginId)) throw loginAttemptBizService.accountLockedException(loginId)
            throw MfaException(ErrorCode.MFA_INVALID_CODE, "코드가 올바르지 않습니다")
        }
        // 5. 소비 — DEL 이 1 을 돌려준 요청만 세션을 받는다. 진 요청은 방금 한 사용 표시(TOTP 구간)를 되돌린다
        if (redisTemplate.delete(key) != true) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            throw invalidChallenge()
        }

        // 1단계 뒤 계정이 비활성화됐으면 세션을 주지 않는다
        val user = userEntityService.getUserById(userId)?.takeIf { it.status == UserStatus.ACTIVE } ?: throw invalidChallenge()
        val provider = challenge[FIELD_PROVIDER]?.let { SocialProvider.valueOf(it) }
        val session = loginCompletionBizService.complete(user, provider, challenge[FIELD_DEVICE_TYPE], challenge[FIELD_DEVICE_INFO], ipAddress, clearAttempts = true)
        auditBizService.logSafely(
            eventType = AuditEventType.MFA_CHALLENGE_SUCCESS,
            eventCategory = AuditBizService.CATEGORY_AUTH,
            userId = userId,
            username = loginId,
            ipAddress = ipAddress,
            description = "2단계 통과 (${origin.name}, ${method.value})",
            sessionId = session.sessionId,
        )
        return MfaCompletionDto(origin = origin, session = session)
    }

    private fun invalidChallenge() = MfaException(ErrorCode.MFA_CHALLENGE_INVALID, "2단계 인증이 만료되었거나 유효하지 않습니다. 다시 로그인하세요")

    private fun key(token: String): String = KEY_PREFIX + tokenHasher.hash(token)

    companion object {
        private const val KEY_PREFIX = "mfa:challenge:"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_LOGIN_ID = "loginId"
        private const val FIELD_ORIGIN = "origin"
        private const val FIELD_PROVIDER = "provider"
        private const val FIELD_DEVICE_TYPE = "deviceType"
        private const val FIELD_DEVICE_INFO = "deviceInfo"
        private const val CHALLENGE_GONE = -1L

        /** 저장 — ARGV[1] 은 TTL(초), 나머지는 필드·값 쌍. HSET 과 EXPIRE 를 한 번에. */
        private val SAVE_SCRIPT: RedisScript<Long> =
            RedisScript.of(
                """
                redis.call('HSET', KEYS[1], unpack(ARGV, 2))
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
                return 1
                """.trimIndent(),
                Long::class.java,
            )

        /**
         * 실패 기록 — 키가 **있을 때만** 실패 수(`failures`, 없으면 0 에서 시작)를 올리고, 한도에 닿으면 지운다.
         * 키가 없으면(만료·소비) 아무것도 만들지 않는다(독립 `HINCRBY` 는 사라진 키를 TTL 없는 해시로 되살린다).
         */
        private val FAIL_SCRIPT: RedisScript<Long> =
            RedisScript.of(
                """
                if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
                local failures = redis.call('HINCRBY', KEYS[1], 'failures', 1)
                if failures >= tonumber(ARGV[1]) then redis.call('DEL', KEYS[1]) end
                return failures
                """.trimIndent(),
                Long::class.java,
            )
    }
}