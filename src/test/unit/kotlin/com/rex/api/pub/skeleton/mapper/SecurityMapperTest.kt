package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.dto.security.BlockedIpDto
import com.rex.api.pub.skeleton.dto.security.LoginAttemptStatusDto
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.security.BlockedIpEntity
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime

/**
 * `is`-prefix Boolean 매핑 회귀.
 *
 * Kotlin `val isActive: Boolean` 의 getter 는 `isActive()` 라 MapStruct 소스 프로퍼티명은 `active` 가 되고,
 * 타깃이 data class 면 생성자 파라미터 `isActive` 와 이름이 맞지 않아 **미매핑 → 항상 false** 였다(IGNORE 정책이 덮고 있던 결함).
 * `@Mapping(source = "active", target = "isActive")` 명시를 이 테스트가 고정한다 — 명시를 지우면 실패한다.
 */
class SecurityMapperTest : BehaviorSpec({
    val mapper = Mappers.getMapper(SecurityMapper::class.java)

    Given("isActive = true 인 BlockedIpEntity") {
        val entity =
            BlockedIpEntity(
                id = 7L,
                ipAddress = "10.0.0.7",
                blockReason = BlockReason.MANUAL,
                blockedAt = LocalDateTime.of(2026, 9, 15, 12, 0),
                isActive = true,
            )

        Then("toDto 의 isActive 가 true 다 — 이전엔 항상 false") {
            val dto = mapper.toDto(entity)
            dto.isActive shouldBe true
            dto.ipAddress shouldBe "10.0.0.7"
        }
    }

    Given("isActive = true · expiresAt = null(영구) 인 BlockedIpDto") {
        val dto =
            BlockedIpDto(
                id = 7L,
                ipAddress = "10.0.0.7",
                blockReason = BlockReason.MANUAL,
                blockedAt = LocalDateTime.of(2026, 9, 15, 12, 0),
                expiresAt = null,
                isActive = true,
            )

        Then("toModel 의 isActive·isPermanent 가 소스와 같다") {
            val model = mapper.toModel(dto)
            model.isActive shouldBe true
            model.isPermanent shouldBe true
        }

        Then("toEntity 의 blockedAt 이 DTO 값 그대로다 — ignore 하면 no-arg 생성자가 초기화식을 실행하지 않아 null 이 된다") {
            val entity = mapper.toEntity(dto)
            entity.blockedAt shouldBe LocalDateTime.of(2026, 9, 15, 12, 0)
            entity.isActive shouldBe true
        }
    }

    Given("isLocked = true 인 LoginAttemptStatusDto") {
        val dto =
            LoginAttemptStatusDto(
                username = "user1",
                currentAttempts = 5,
                maxAttempts = 5,
                isLocked = true,
                remainingLockTimeSeconds = 300L,
            )

        Then("toLoginAttemptStatusModel 의 isLocked 가 true 다") {
            mapper.toLoginAttemptStatusModel(dto).isLocked shouldBe true
        }
    }
})