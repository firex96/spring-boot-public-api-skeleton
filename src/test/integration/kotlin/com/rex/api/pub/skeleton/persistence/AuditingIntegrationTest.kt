package com.rex.api.pub.skeleton.persistence

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import com.rex.api.pub.skeleton.persistence.jpa.entity.main.SoftDeletableExampleEntity
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.time.LocalDateTime

/**
 * JPA Auditing 이 감사 4필드를 자동으로 채우는지 검증한다.
 *
 * **사용자 식별원은 `AuditorContext` 다** — 레포별 필터가 채우고
 * `SimpleAuditorAware` 가 그것만 읽는다. **MDC 는 감사에 관여하지 않는다.**
 *
 * 이 테스트는 `AuditorContext` 에 값을 **직접** 넣으므로 채우는 경로와 무관하다 —
 * 감사 쪽 계약만 본다. 「헤더·인증 → 홀더」구간은
 * `AuditorChainIntegrationTest`(신뢰 켠 경우)와
 * `AuditorHeaderDistrustIntegrationTest`(기본값)가 본다.
 *
 * 대상 엔티티로 `SoftDeletableExampleEntity` 를 쓴다 — `BaseAuditedEntity` 를 상속하면서
 * 필수 필드가 `name` 하나뿐이라 감사 필드 외의 실패 요인이 없다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.persistence.AuditingIntegrationTest"
 * ```
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class AuditingIntegrationTest(
    @Qualifier("mainEntityManagerFactory") private val entityManagerFactory: EntityManagerFactory,
) : BehaviorSpec() {

    init {
        // 남으면 다음 테스트의 감사값이 오염된다.
        afterTest { AuditorContext.clear() }

        Given("사용자 식별원이 비어 있을 때") {
            withUser(null)
            lateinit var beforeReload: AuditSnapshot
            val snapshot =
                inRolledBackTransaction { entityManager ->
                    val entity = SoftDeletableExampleEntity(name = "audit-anonymous")
                    entityManager.persist(entity)
                    entityManager.flush()
                    // DB 를 다녀오기 전의 값이다 — 컬럼 기본값으로는 만들어질 수 없다.
                    beforeReload = snapshotOf(entity)
                    entityManager.clear()
                    snapshotOf(entityManager.find(SoftDeletableExampleEntity::class.java, entity.id))
                }

            When("감사 대상 엔티티를 저장하면") {
                // 재조회 값만 보면 부족하다 — batch 의 `sample` 은
                // `created_at DEFAULT CURRENT_TIMESTAMP` 라 리스너가 죽어도
                // 컬럼 기본값이 시각을 채워 통과할 수 있다.
                Then("A-1 flush 시점에 감사 리스너가 시각을 채운다") {
                    beforeReload.createdAt.shouldNotBeNull()
                    beforeReload.updatedAt.shouldNotBeNull()
                }

                Then("A-1 생성·수정 시각이 채워진다") {
                    snapshot.createdAt.shouldNotBeNull()
                    snapshot.updatedAt.shouldNotBeNull()
                }

                Then("A-1 생성자·수정자가 SYSTEM_USER_ID 가 된다") {
                    snapshot.createdBy shouldBe Constant.SYSTEM_USER_ID
                    snapshot.updatedBy shouldBe Constant.SYSTEM_USER_ID
                }
            }
        }

        Given("감사 주체가 42 일 때") {
            withUser(42L)
            val snapshot =
                inRolledBackTransaction { entityManager ->
                    val entity = SoftDeletableExampleEntity(name = "audit-user-42")
                    entityManager.persist(entity)
                    entityManager.flush()
                    entityManager.clear()
                    snapshotOf(entityManager.find(SoftDeletableExampleEntity::class.java, entity.id))
                }

            When("감사 대상 엔티티를 저장하면") {
                Then("A-2 생성자·수정자가 모두 42 가 된다") {
                    snapshot.createdBy shouldBe 42L
                    snapshot.updatedBy shouldBe 42L
                }
            }
        }

        Given("42 로 저장한 행을 7 이 수정할 때") {
            withUser(42L)
            // 수정 시점에 다시 쓰이지 않으면 이 값이 그대로 남는다.
            val staleTimestamp = LocalDateTime.of(2000, 1, 1, 0, 0)
            lateinit var created: AuditSnapshot
            val snapshot =
                inRolledBackTransaction { entityManager ->
                    val entity = SoftDeletableExampleEntity(name = "audit-created-by-42")
                    entityManager.persist(entity)
                    entityManager.flush()
                    entityManager.clear()

                    // 생성 직후 값도 DB 에서 읽는다 — 메모리 값과 비교하면 DB 의
                    // 초 단위 절삭 때문에 같은 시각이 다르게 보인다.
                    val reloaded = entityManager.find(SoftDeletableExampleEntity::class.java, entity.id)
                    created = snapshotOf(reloaded)

                    withUser(7L)
                    reloaded.name = "audit-updated-by-7"
                    reloaded.updatedAt = staleTimestamp
                    entityManager.flush()

                    entityManager.clear()
                    snapshotOf(entityManager.find(SoftDeletableExampleEntity::class.java, entity.id))
                }

            When("수정 후 다시 읽으면") {
                // 생성자가 수정자와 함께 바뀌면 감사 기록이 무의미해진다.
                Then("A-3 createdBy 는 42 로 남는다") {
                    snapshot.createdBy shouldBe 42L
                }

                Then("A-3 updatedBy 만 7 로 바뀐다") {
                    snapshot.updatedBy shouldBe 7L
                }

                Then("A-3 createdAt 은 생성 시각 그대로다") {
                    snapshot.createdAt shouldBe created.createdAt
                }

                // 「직전 값보다 크다」로는 DATETIME 초 절삭 때문에 잡히지 않는다.
                Then("A-3 updatedAt 은 수정 시점으로 다시 쓰인다") {
                    snapshot.updatedAt.shouldNotBeNull()
                    snapshot.updatedAt.isAfter(staleTimestamp) shouldBe true
                }
            }
        }
    }

    private data class AuditSnapshot(
        val createdBy: Long,
        val updatedBy: Long,
        val createdAt: LocalDateTime?,
        val updatedAt: LocalDateTime?,
    )

    private fun snapshotOf(entity: SoftDeletableExampleEntity) =
        AuditSnapshot(
            createdBy = entity.createdBy,
            updatedBy = entity.updatedBy,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * 사용자 식별원을 세운다. `null` 이면 비운다.
     *
     * 운영 경로와 같은 지점에 값을 놓는다 — `SimpleAuditorAware` 는 오직 여기만 읽는다.
     */
    private fun withUser(userId: Long?) {
        AuditorContext.set(userId)
    }

    /**
     * 커밋하지 않는다 — 검증이 끝나면 롤백해 다음 테스트에 행을 남기지 않는다.
     *
     * `flush` 로 INSERT/UPDATE 를 내보내고 `clear` 로 영속성 컨텍스트를 비운 뒤 다시 읽는다.
     * 그래야 **DB 에 실제로 들어간 값**을 보게 된다 — 메모리 위의 객체를 그대로 보면
     * 감사 리스너가 동작하지 않아도 통과할 수 있다.
     */
    private fun <T> inRolledBackTransaction(block: (EntityManager) -> T): T {
        val entityManager = entityManagerFactory.createEntityManager()
        return try {
            entityManager.transaction.begin()
            block(entityManager)
        } finally {
            if (entityManager.transaction.isActive) {
                entityManager.transaction.rollback()
            }
            entityManager.close()
        }
    }
}