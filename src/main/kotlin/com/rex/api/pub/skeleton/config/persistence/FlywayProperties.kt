package com.rex.api.pub.skeleton.config.persistence

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Flyway 마이그레이션 locations 설정.
 *
 * 프로파일별로 다른 locations를 지정하여 DDL/DML을 분리 관리합니다.
 *
 * ## 디렉토리 구조
 * ```
 * db/migration/{schema}/
 * ├── ddl/           # 테이블 생성, 수정 등 스키마 변경
 * └── dml/
 *     ├── mandatory/ # 운영에 필수적인 기본 데이터
 *     └── test/      # 개발/테스트 환경 전용 데이터
 * ```
 *
 * ## 프로파일별 설정
 * - prod: ddl + dml/mandatory
 * - local/dev: ddl + dml/mandatory + dml/test
 *
 * @see MysqlFlywayConfiguration
 */
@ConfigurationProperties(prefix = "flyway")
data class FlywayProperties(
    val auth: SchemaLocations = SchemaLocations(),
) {
    data class SchemaLocations(
        val locations: List<String> = emptyList(),
    )
}