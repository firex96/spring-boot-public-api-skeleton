package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.context.AuditorContext
import com.rex.api.pub.skeleton.persistence.jpa.entity.main.SoftDeletableExampleEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 감사 주체를 **실제 요청 안에서** 관측하기 위한 테스트 전용 엔드포인트.
 *
 * ## 왜 필요한가
 *
 * 필터를 직접 순서대로 호출하면 등록이 빠지거나 순서가 틀려도 통과한다 — 테스트 코드에
 * 내가 적은 순서를 검증할 뿐이다. 실제 체인을 지나야 **등록·순서·요청 생명주기**까지
 * 함께 검증된다.
 *
 * ## 왜 `/demo` 인가
 *
 * `SecurityConfiguration` 이 `/demo` 하위를 permitAll 로 둔다. permitAll 은 인증을
 * 막지 않으므로 **인증·비인증을 같은 엔드포인트로** 볼 수 있다.
 * **운영 코드에 테스트용 엔드포인트를 만들지 않는다.**
 *
 * ## 응답 형식
 *
 * `<체인 안에서 관측한 감사 주체>:<저장된 행의 created_by>` — 예: `42:42`, `null:0`.
 *
 * 둘을 함께 돌려주는 이유가 있다. `created_by` 만 보면 `SYSTEM_USER_ID` 가 0 이고
 * Kotlin 기본값도 0 이라 **감사 리스너가 아예 안 붙어도** 기대값과 같아진다.
 * 관측값이 함께 있어야 「필터가 넣지 않았다」가 확정된다.
 */
@TestConfiguration
class AuditProbeConfiguration {
    @RestController
    @RequestMapping(PATH)
    class AuditProbeController(
        @Qualifier("mainEntityManagerFactory") private val entityManagerFactory: EntityManagerFactory,
    ) {
        @PostMapping
        fun save(): String {
            val observed = AuditorContext.get()
            val createdBy = inRolledBackTransaction { it.persistAndReadCreatedBy() }
            return "$observed:$createdBy"
        }

        /**
         * `flush → clear → find` 로 DB 왕복을 강제한다. 메모리 위의 객체를 그대로 보면
         * 감사 리스너가 동작하지 않아도 통과할 수 있다.
         */
        private fun EntityManager.persistAndReadCreatedBy(): Long {
            val entity = SoftDeletableExampleEntity(name = "audit-probe")
            persist(entity)
            flush()
            clear()
            return find(SoftDeletableExampleEntity::class.java, entity.id).createdBy
        }

        /** 커밋하지 않는다 — 검증이 끝나면 롤백해 다음 테스트에 행을 남기지 않는다. */
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

    companion object {
        const val PATH = "/demo/audit-probe"

        /** 프로브 응답을 조립한다. 기대값을 문자열 리터럴로 흩뿌리지 않는다. */
        fun body(
            observed: Long?,
            createdBy: Long,
        ): String = "$observed:$createdBy"
    }
}