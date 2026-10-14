package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.dto.user.UserSessionDto
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime

/**
 * `is`-prefix Boolean 매핑 회귀 — 세션 목록의 `isCurrentSession` 이 항상 false 로 내려가던 결함을 고정한다.
 * `@Mapping(source = "currentSession", target = "isCurrentSession")` 을 지우면 실패한다.
 */
class AuthMapperTest : BehaviorSpec({
    val mapper = Mappers.getMapper(AuthMapper::class.java)

    Given("isCurrentSession = true 인 UserSessionDto") {
        val dto =
            UserSessionDto(
                id = 1L,
                sessionId = "abcd****",
                deviceType = "WEB",
                deviceInfo = null,
                ipAddress = "127.0.0.1",
                lastActivityAt = LocalDateTime.of(2026, 9, 15, 12, 0),
                createdAt = LocalDateTime.of(2026, 9, 15, 11, 0),
                isCurrentSession = true,
            )

        Then("toUserSessionModel 의 isCurrentSession 이 true 다 — 이전엔 항상 false") {
            val model = mapper.toUserSessionModel(dto)
            model.isCurrentSession shouldBe true
            model.sessionId shouldBe "abcd****"
        }
    }
})