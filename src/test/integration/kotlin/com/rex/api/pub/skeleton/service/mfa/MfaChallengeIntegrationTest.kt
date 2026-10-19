package com.rex.api.pub.skeleton.service.mfa

import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.dto.auth.LoginOutcomeDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.MfaException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserMfaEntityService
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import com.rex.api.pub.skeleton.util.TotpCodes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 2단계 challenge 와 코드 재사용 방지 — 실제 MySQL(조건부 UPDATE)·Redis(해시·스크립트)에서.
 *
 * 로그인 보호(계정 잠금)를 켠다 — testcontainers 프로필은 꺼 두고, 꺼진 상태의 잠금 확인은 항상 false 다.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
@TestPropertySource(properties = ["security.login-protection.enable-brute-force-protection=true"])
class MfaChallengeIntegrationTest(
    private val userEntityService: UserEntityService,
    private val passwordEncoder: PasswordEncoder,
    private val mfaBizService: MfaBizService,
    private val mfaChallengeBizService: MfaChallengeBizService,
    private val authBizService: AuthBizService,
    private val loginAttemptBizService: LoginAttemptBizService,
    private val redisTemplate: StringRedisTemplate,
    private val userMfaEntityService: UserMfaEntityService,
    private val applicationContext: ApplicationContext,
) : BehaviorSpec({
    val authTransaction = TransactionTemplate(applicationContext.getBean("authTransactionManager", PlatformTransactionManager::class.java))
    val password = "Password123!"

    fun newUser(): UserDto =
        userEntityService.createUser(
            loginId = "it_mfa_${UUID.randomUUID().toString().take(8)}",
            passwordHash = passwordEncoder.encode(password).shouldNotBeNull(),
            email = null,
            name = null,
            nickname = null,
            phone = null,
        )

    /** 2차 인증을 켠 새 사용자와 그 인증 앱, 활성화가 준 복구 코드. */
    fun enrolledUserWithRecoveryCodes(): Triple<UserDto, TotpCodes, List<String>> {
        val created =
            userEntityService.createUser(
                loginId = "it_mfa_${UUID.randomUUID().toString().take(8)}",
                passwordHash = passwordEncoder.encode(password).shouldNotBeNull(),
                email = null,
                name = null,
                nickname = null,
                phone = null,
            )
        val codes = TotpCodes(mfaBizService.setup(created.id).secret)
        val recoveryCodes = mfaBizService.activate(created.id, codes.next(), currentSessionId = "none").recoveryCodes
        return Triple(userEntityService.getUserById(created.id).shouldNotBeNull(), codes, recoveryCodes)
    }

    fun enrolledUser(): Pair<UserDto, TotpCodes> = enrolledUserWithRecoveryCodes().let { (user, codes, _) -> user to codes }

    fun challengeKeys(): Set<String> = redisTemplate.keys("mfa:challenge:*")

    /** 2차 인증을 켠 사용자의 1단계 통과 — 관문이 세션 대신 challenge 를 준다. */
    fun challengeFor(user: UserDto, origin: MfaOrigin) =
        mfaChallengeBizService.afterFirstFactor(user, origin, null, "WEB", null, null).shouldBeInstanceOf<LoginOutcomeDto.MfaRequired>().challenge

    Given("MC-1 같은 TOTP 구간의 코드") {
        val (user, codes) = enrolledUser()
        val code = codes.next()

        Then("첫 번째만 통과하고 두 번째는 거부한다 — DB 조건부 UPDATE") {
            mfaBizService.verifyCode(user.id, MfaMethod.TOTP, code) shouldBe true
            mfaBizService.verifyCode(user.id, MfaMethod.TOTP, code) shouldBe false
        }
    }

    Given("MC-2 같은 challenge 에 맞는 코드 8건을 동시에 낸다") {
        val (user, codes) = enrolledUser()
        val challenge = challengeFor(user, MfaOrigin.API_PASSWORD)
        val code = codes.next()

        Then("세션은 정확히 하나만 나가고, 진 요청은 실패로 세지 않는다 — 계정이 잠기지 않는다") {
            val results =
                withContext(Dispatchers.IO) {
                    (1..8).map { async { runCatching { mfaChallengeBizService.complete(challenge.token, MfaOrigin.API, MfaMethod.TOTP, code, null) } } }.awaitAll()
                }
            results.count { it.isSuccess } shouldBe 1
            loginAttemptBizService.getCurrentAttempts(user.loginId) shouldBe 0
            loginAttemptBizService.isUserLocked(user.loginId) shouldBe false
        }
    }

    Given("MC-2R 같은 challenge 에 서로 다른 유효한 복구 코드 8개를 동시에 낸다") {
        val (user, _, recoveryCodes) = enrolledUserWithRecoveryCodes()
        val challenge = challengeFor(user, MfaOrigin.API_PASSWORD)

        Then("세션은 정확히 하나만 나간다 — 코드마다 재사용 방지를 지나도 challenge 소비(DEL)는 한 번이다") {
            val results =
                withContext(Dispatchers.IO) {
                    recoveryCodes.take(8).map { code ->
                        async { runCatching { mfaChallengeBizService.complete(challenge.token, MfaOrigin.API, MfaMethod.RECOVERY, code, null) } }
                    }.awaitAll()
                }
            results.count { it.isSuccess } shouldBe 1
            userMfaEntityService.countUnusedRecoveryCodes(user.id) shouldBe 9 // 진 요청의 사용 표시는 롤백된다
            loginAttemptBizService.getCurrentAttempts(user.loginId) shouldBe 0
        }
    }

    Given("MC-2S 같은 복구 코드") {
        val (user, _, recoveryCodes) = enrolledUserWithRecoveryCodes()

        Then("첫 번째만 통과하고(대소문자·하이픈 무시) 두 번째는 거부한다 — DB 조건부 UPDATE") {
            mfaBizService.verifyCode(user.id, MfaMethod.RECOVERY, recoveryCodes[0].lowercase().replace("-", "")) shouldBe true
            mfaBizService.verifyCode(user.id, MfaMethod.RECOVERY, recoveryCodes[0]) shouldBe false
        }
    }

    Given("MC-3 API challenge 를 브라우저 완료 경로로 낸다") {
        val (user, codes) = enrolledUser()
        val challenge = challengeFor(user, MfaOrigin.API_PASSWORD)

        Then("코드를 보지 않고 거부하고 challenge 는 남긴다 — 정상 경로로는 계속 된다") {
            shouldThrow<MfaException> {
                mfaChallengeBizService.complete(challenge.token, MfaOrigin.BROWSER, MfaMethod.TOTP, codes.next(), null)
            }.errorCode shouldBe ErrorCode.MFA_CHALLENGE_INVALID
            mfaChallengeBizService.isPending(challenge.token, MfaOrigin.API) shouldBe true
            mfaChallengeBizService.complete(challenge.token, MfaOrigin.API, MfaMethod.TOTP, codes.next(), null).origin shouldBe MfaOrigin.API_PASSWORD
        }
    }

    Given("MC-4 같은 challenge 에서 한도(5)만큼 틀린다") {
        val (user, codes) = enrolledUser()
        val challenge = challengeFor(user, MfaOrigin.API_PASSWORD)

        Then("challenge 가 지워지고, 지워진 뒤의 실패 기록은 키를 되살리지 않는다") {
            repeat(5) { runCatching { mfaChallengeBizService.complete(challenge.token, MfaOrigin.API, MfaMethod.TOTP, codes.wrong(), null) } }
            mfaChallengeBizService.isPending(challenge.token, MfaOrigin.API) shouldBe false
            val before = challengeKeys()
            shouldThrow<MfaException> {
                mfaChallengeBizService.complete(challenge.token, MfaOrigin.API, MfaMethod.TOTP, codes.wrong(), null)
            }.errorCode shouldBe ErrorCode.MFA_CHALLENGE_INVALID
            challengeKeys() shouldBe before
            loginAttemptBizService.unlockUserAccount(user.loginId)
        }
    }

    Given("MC-5 challenge 를 받은 뒤 계정이 잠겼다") {
        val (user, codes) = enrolledUser()
        val challenge = challengeFor(user, MfaOrigin.AUTHORIZE)
        repeat(5) { loginAttemptBizService.recordFailedAttempt(user.loginId, null) }

        Then("맞는 코드라도 코드를 보기 전에 423 — challenge 는 소비되지 않는다") {
            shouldThrow<AccountLockedException> {
                mfaChallengeBizService.complete(challenge.token, MfaOrigin.BROWSER, MfaMethod.TOTP, codes.next(), null)
            }
            mfaChallengeBizService.isPending(challenge.token, MfaOrigin.BROWSER) shouldBe true
            loginAttemptBizService.unlockUserAccount(user.loginId)
        }
    }

    Given("MC-7 활성화 트랜잭션이 등록 행을 잡고 있는 동안 비밀번호 1단계를 통과한다") {
        val created = newUser()
        val codes = TotpCodes(mfaBizService.setup(created.id).secret)
        val user = userEntityService.getUserById(created.id).shouldNotBeNull()

        Then("로그인은 활성화가 끝날 때까지 기다렸다가 세션 대신 challenge 를 받는다 — 2차 인증 없는 세션이 생기지 않는다") {
            val activating = CountDownLatch(1)
            val outcome =
                withContext(Dispatchers.IO) {
                    val activation =
                        async {
                            try {
                                authTransaction.execute {
                                    mfaBizService.activate(created.id, codes.next(), currentSessionId = "none")
                                    activating.countDown()
                                    Thread.sleep(800) // 활성화를 커밋하기 전에 경쟁 요청이 들어온다
                                    System.nanoTime() // 잠금을 쥔 채 기다린 시간이 끝나는 시각 — 경쟁 요청은 이보다 늦게 끝나야 한다
                                }.shouldNotBeNull()
                            } finally {
                                activating.countDown() // 활성화가 먼저 실패해도 대기를 풀어 테스트가 멈추지 않게
                            }
                        }
                    activating.await(10, TimeUnit.SECONDS) shouldBe true
                    val login = async { mfaChallengeBizService.afterFirstFactor(user, MfaOrigin.API_PASSWORD, null, "WEB", null, null) to System.nanoTime() }
                    val heldUntil = activation.await()
                    login.await().also { (_, finishedAt) -> (finishedAt >= heldUntil) shouldBe true } // 로그인은 잠금이 풀릴 때까지 기다렸다
                }
            outcome.first.shouldBeInstanceOf<LoginOutcomeDto.MfaRequired>()
        }
    }

    Given("MC-8 활성화가 커밋되기 전에 등록 시작(setup)이 다시 들어온다") {
        val created = newUser()
        val codes = TotpCodes(mfaBizService.setup(created.id).secret)

        Then("늦은 setup 은 켜진 등록을 덮지 못하고 409 — 등록은 ACTIVE 로 남는다") {
            val activating = CountDownLatch(1)
            val setupResult =
                withContext(Dispatchers.IO) {
                    val activation =
                        async {
                            try {
                                authTransaction.execute {
                                    mfaBizService.activate(created.id, codes.next(), currentSessionId = "none")
                                    activating.countDown()
                                    Thread.sleep(800)
                                    System.nanoTime() // 잠금을 쥔 채 기다린 시간이 끝나는 시각 — 경쟁 요청은 이보다 늦게 끝나야 한다
                                }.shouldNotBeNull()
                            } finally {
                                activating.countDown()
                            }
                        }
                    activating.await(10, TimeUnit.SECONDS) shouldBe true
                    val setup = async { runCatching { mfaBizService.setup(created.id) } to System.nanoTime() }
                    val heldUntil = activation.await()
                    setup.await().also { (_, finishedAt) -> (finishedAt >= heldUntil) shouldBe true } // setup 은 잠금이 풀릴 때까지 기다렸다
                }
            (setupResult.first.exceptionOrNull() as MfaException).errorCode shouldBe ErrorCode.MFA_ALREADY_ENABLED
            mfaBizService.isEnabled(created.id) shouldBe true
        }
    }

    Given("MC-6 2차 인증 계정이 비밀번호 실패 3회 뒤 비밀번호로 1단계를 통과한다") {
        val (user, _) = enrolledUser()
        repeat(3) { loginAttemptBizService.recordFailedAttempt(user.loginId, null) }

        Then("challenge 를 받고 실패 카운터는 그대로 3 — 1단계 성공은 누적을 지우지 않는다") {
            authBizService.login(LoginRequestDto(loginId = user.loginId, password = password), null).shouldBeInstanceOf<LoginOutcomeDto.MfaRequired>()
            loginAttemptBizService.getCurrentAttempts(user.loginId) shouldBe 3
            loginAttemptBizService.unlockUserAccount(user.loginId)
        }
    }
})