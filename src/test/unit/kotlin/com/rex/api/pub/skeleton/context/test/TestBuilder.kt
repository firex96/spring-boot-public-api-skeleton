package com.rex.api.pub.skeleton.context.test

import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import com.rex.api.pub.skeleton.context.FixtureMonkeyFactory
import com.rex.api.pub.skeleton.dto.TestDto

/**
 * TestDto 테스트 픽스처 빌더.
 */
object TestBuilder {
    /**
     * TestDto 빌더를 반환합니다.
     *
     * 사용 예시:
     * ```kotlin
     * val testDto = TestBuilder.testDtoBuilder()
     *     .set(TestDto::id, 0)  // 새 엔티티
     *     .sample()
     * ```
     */
    fun testDtoBuilder() =
        FixtureMonkeyFactory.fixtureMonkey
            .giveMeKotlinBuilder<TestDto>()
            .setExp(TestDto::id, 0)
}