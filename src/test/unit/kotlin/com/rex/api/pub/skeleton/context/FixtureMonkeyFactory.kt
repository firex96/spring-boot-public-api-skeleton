package com.rex.api.pub.skeleton.context

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin

/**
 * FixtureMonkey 인스턴스 팩토리.
 *
 * 테스트용 객체 생성에 사용되는 FixtureMonkey 인스턴스를 제공합니다.
 * 모든 Builder에서 동일한 설정을 사용하도록 중앙화합니다.
 */
object FixtureMonkeyFactory {
    /**
     * 기본 FixtureMonkey 인스턴스.
     *
     * Kotlin data class와 호환되는 설정이 적용되어 있습니다.
     */
    val fixtureMonkey: FixtureMonkey =
        FixtureMonkey
            .builder()
            .plugin(KotlinPlugin())
            .build()
}