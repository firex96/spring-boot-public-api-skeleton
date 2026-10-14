package com.rex.api.pub.skeleton.persistence

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.dto.audit.AuditLogDto
import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.PasswordResetTokenRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.RoleRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserGroupRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.audit.AuditLogRepository
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.audit.AuditLogEntityService
import com.rex.api.pub.skeleton.service.auth.GroupManagementBizService
import com.rex.api.pub.skeleton.service.auth.UserGroupBizService
import com.rex.api.pub.skeleton.service.user.UserSelfBizService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * auth 서비스의 `@Transactional` 이 **auth 트랜잭션 매니저**에 묶여 있음을 실제 DB(Testcontainers, auth 풀 1/1)로 증명한다.
 *
 * primary(default) 트랜잭션 매니저로 묵시 바인딩되면 — (a) 인터페이스 파생 `deleteBy…` 가 조용히 버려지고,
 * (b) `@Modifying` 이 `TransactionRequiredException` 을 내고, (c) 바깥 auth 트랜잭션의 롤백이 안쪽 쓰기에 미치지 않는다.
 * (d) 는 `AuditLogEntityService.create` 가 호출자의 auth 커넥션에 참여해 풀 1 에서 대기하지 않음을 본다
 * (auth `REQUIRES_NEW` 였다면 두 번째 커넥션을 기다려 `connection-timeout` 에 실패한다).
 *
 * 클래스 레벨 `@Transactional` 을 두지 않는다 — 서비스 자신의 트랜잭션 경계를 관찰해야 한다.
 *
 * ```bash
 * ./gradlew integrationTest --tests "*.persistence.AuthTransactionBindingIntegrationTest"
 * ```
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class AuthTransactionBindingIntegrationTest(
    private val userGroupBizService: UserGroupBizService,
    private val groupManagementBizService: GroupManagementBizService,
    private val userSelfBizService: UserSelfBizService,
    private val auditLogEntityService: AuditLogEntityService,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userGroupRepository: UserGroupRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val auditLogRepository: AuditLogRepository,
    @Qualifier("authTransactionManager") private val authTransactionManager: PlatformTransactionManager,
) : BehaviorSpec() {

    init {
        val suffix = UUID.randomUUID().toString().take(8)
        val createdUserIds = mutableListOf<Long>()
        val createdGroupIds = mutableListOf<Long>()

        fun newUser(tag: String): UserEntity =
            userRepository.save(
                UserEntity(loginId = "txbind_${tag}_$suffix", passwordHash = "n/a", email = "txbind_${tag}_$suffix@example.com"),
            ).also { createdUserIds += it.id }

        fun newGroup(tag: String): Long =
            groupManagementBizService.createGroup(CreateRoleRequestDto(code = "TXBIND_${tag}_${suffix.uppercase()}", name = "tx binding $tag")).id
                .also { createdGroupIds += it }

        afterSpec {
            createdGroupIds.forEach { groupId -> userGroupRepository.deleteAll(userGroupRepository.findByGroupId(groupId)) }
            createdUserIds.forEach { userId ->
                passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUserIdAndIsUsedFalseOrderByCreatedAtDesc(userId))
            }
            roleRepository.deleteAllById(createdGroupIds)
            userRepository.deleteAllById(createdUserIds)
        }

        // 준비 코드도 Then 안에 둔다 — Given/When 레벨에서 예외가 나면 Kotest 가 컨테이너 오류로 처리해 실패 원인이 묻힌다
        Given("(a) 그룹에 할당된 사용자") {
            When("removeUserFromGroup 을 호출하면") {
                Then("파생 deleteBy… 가 auth 트랜잭션에서 flush 되어 할당이 실제로 사라진다") {
                    val user = newUser("a")
                    val groupId = newGroup("A")
                    userGroupBizService.assignUserToGroup(user.id, groupId, assignedBy = null)
                    userGroupRepository.existsByUserIdAndGroupId(user.id, groupId) shouldBe true

                    userGroupBizService.removeUserFromGroup(user.id, groupId)

                    userGroupRepository.existsByUserIdAndGroupId(user.id, groupId) shouldBe false
                }
            }
        }

        Given("(b) 이메일이 있는 사용자") {
            When("requestPasswordReset 을 호출하면") {
                Then("@Modifying 무효화 뒤 토큰이 저장된다 (TransactionRequiredException 없음)") {
                    val user = newUser("b")

                    userSelfBizService.requestPasswordReset(user.email!!)

                    passwordResetTokenRepository.findByUserIdAndIsUsedFalseOrderByCreatedAtDesc(user.id) shouldHaveSize 1
                }
            }
        }

        Given("(c) 바깥 auth 트랜잭션") {
            When("안에서 createGroup 한 뒤 setRollbackOnly 하면") {
                Then("안쪽 쓰기가 바깥 auth 트랜잭션에 참여했으므로 함께 롤백된다") {
                    val code = "TXBIND_C_${suffix.uppercase()}"

                    TransactionTemplate(authTransactionManager).execute { status ->
                        groupManagementBizService.createGroup(CreateRoleRequestDto(code = code, name = "tx binding c"))
                        status.setRollbackOnly()
                    }

                    roleRepository.existsByCode(code) shouldBe false
                }
            }
        }

        Given("(d) 유일한 auth 커넥션을 쥔 바깥 트랜잭션") {
            When("먼저 auth 쿼리로 커넥션을 점유한 뒤 auditLogEntityService.create 를 호출하면") {
                Then("같은 커넥션에 참여해 풀 1 에서 대기 없이 완료되고 감사 행이 남는다") {
                    val before = auditLogRepository.count()

                    TransactionTemplate(authTransactionManager).execute {
                        userRepository.count()
                        auditLogEntityService.create(
                            AuditLogDto(eventType = AuditEventType.LOGIN_FAILURE, eventCategory = "AUTH", username = "txbind_d_$suffix"),
                        )
                    }

                    auditLogRepository.count() shouldBe before + 1
                }
            }
        }
    }
}