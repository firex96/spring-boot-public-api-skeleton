package com.rex.api.pub.skeleton.config

import io.kotest.core.config.AbstractProjectConfig

/**
 * Unit Test용 Kotest 설정.
 *
 * Unit Test는 Spring Context를 로드하지 않으므로 SpringExtension이 불필요합니다.
 */
object KotestUnitProjectConfig : AbstractProjectConfig()