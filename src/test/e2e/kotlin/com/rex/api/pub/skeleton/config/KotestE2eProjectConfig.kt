package com.rex.api.pub.skeleton.config

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringAutowireConstructorExtension
import io.kotest.extensions.spring.SpringExtension

object KotestE2eProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(
        // Kotest 6 에서 SpringExtension 이 생성자 주입을 더 이상 하지 않는다.
        // 이 확장을 함께 등록하지 않으면 생성자 파라미터가 있는 Spec 이
        // "should have a zero-arg constructor" 로 전부 실패한다.
        SpringAutowireConstructorExtension,
        SpringExtension(),
    )
}