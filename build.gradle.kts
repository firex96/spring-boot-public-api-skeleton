@file:Suppress("UnstableApiUsage")

plugins {
    val kotlinVersion = "2.3.21"

    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("kapt") version kotlinVersion

    idea
    war

    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
}

group = "com.rex"

version = "1.0.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

ktlint {
    version.set("1.7.1")
}

// Version constants
val mapstructVersion = "1.6.3"

// 비밀값 출처 프로필(aws·azure·gcp)용 클라우드 BOM — 파생 프로젝트는 쓰지 않는 클라우드의 BOM·starter 를 지운다
val springCloudAwsVersion = "4.2.0"
val springCloudAzureVersion = "7.4.0"
val springCloudGcpVersion = "8.2.4"
val jjwtVersion = "0.12.6"

dependencies {
    kapt("org.mapstruct:mapstruct-processor:$mapstructVersion")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.github.gavlyukovskiy:datasource-proxy-spring-boot-starter:2.0.0")
    implementation("com.github.gavlyukovskiy:flexy-pool-spring-boot-starter:2.0.0")
    implementation("com.github.loki4j:loki-logback-appender:2.0.1")

    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("jakarta.persistence:jakarta.persistence-api")

    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.json:json:20250517")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // 브라우저 흐름(소셜 로그인·인가 로그인·동의) 세션 — API 는 STATELESS·Bearer 그대로다
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Thymeleaf
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // JWT (jjwt)
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    implementation("org.springframework.data:spring-data-envers")

    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:$springCloudAwsVersion"))
    implementation(platform("com.azure.spring:spring-cloud-azure-dependencies:$springCloudAzureVersion"))
    implementation(platform("com.google.cloud:spring-cloud-gcp-dependencies:$springCloudGcpVersion"))

    runtimeOnly("com.mysql:mysql-connector-j")
    // 비밀값 출처 — 기본은 꺼져 있고 aws·azure·gcp 프로필이 각자 켠다
    runtimeOnly("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager")
    runtimeOnly("com.azure.spring:spring-cloud-azure-starter-keyvault")
    runtimeOnly("com.google.cloud:spring-cloud-gcp-starter-secretmanager")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient")
    testImplementation("org.springframework.security:spring-security-test")

    testImplementation("com.navercorp.fixturemonkey:fixture-monkey-starter-kotlin:1.1.15")
    testImplementation("com.ninja-squad:springmockk:5.0.1")
    testImplementation("io.kotest:kotest-runner-junit5:6.2.4")
    testImplementation("io.kotest:kotest-extensions-spring:6.2.4")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.github.classgraph:classgraph:4.8.186") // MapStruct 애노테이션(CLASS retention)을 클래스 파일에서 읽는 규약 검사용

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-localstack")
    testImplementation("software.amazon.awssdk:secretsmanager")
    testImplementation("com.github.nagyesta.lowkey-vault:lowkey-vault-testcontainers:7.3.127") // azure 프로필 검증용 Key Vault 에뮬레이터
    testImplementation("org.testcontainers:testcontainers-mysql")

    testRuntimeOnly("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// Unit Test Source Set
sourceSets {
    test {
        kotlin.srcDir("src/test/unit/kotlin")
        resources.srcDir("src/test/unit/resources")
    }
}

// Integration Test Source Set
sourceSets.register("integrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    kotlin.srcDir("src/test/integration/kotlin")
    resources.srcDir("src/test/integration/resources")
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

// E2E Test Source Set
sourceSets.register("e2eTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    kotlin.srcDir("src/test/e2e/kotlin")
    resources.srcDir("src/test/e2e/resources")
}

val e2eTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val e2eTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

// Playwright for browser automation E2E tests
dependencies {
    e2eTestImplementation("com.microsoft.playwright:playwright:1.52.0")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

// Integration Test Resources - handle duplicates
tasks.named<Copy>("processIntegrationTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Integration Test Task
// Kotest 6 은 AbstractProjectConfig 를 자동 탐색하지 않는다 — FQN 을 명시해야 한다.
// 소스셋마다 설정이 다르고 integrationTest·e2eTest 클래스패스가 test 출력을 포함하므로
// 클래스 이름을 나누고 태스크별로 가리킨다.
tasks.test {
    systemProperty("kotest.framework.config.fqn", "com.rex.api.pub.skeleton.config.KotestUnitProjectConfig")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests"
    group = "verification"
    // @ActiveProfiles 를 빠뜨린 테스트도 기본 yml 의 논리 키가 해석되도록 testcontainers 를 기본 프로필로 둔다
    systemProperty("spring.profiles.active", "testcontainers")
    systemProperty("kotest.framework.config.fqn", "com.rex.api.pub.skeleton.config.KotestIntegrationProjectConfig")
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()

    // Testcontainers 와 함께 도는 테스트 JVM 힙 — CI 러너(메모리 16GB)에서도 컨테이너와 같이 돌 수 있게 둔다
    maxHeapSize = "4g"
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "-XX:+UseG1GC",
        "-XX:MaxMetaspaceSize=1g",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=${project.layout.buildDirectory.get()}/heapdump.hprof",
    )

    // 순차 실행 (병렬 실행 시 메모리 부족 가능)
    maxParallelForks = 1

    shouldRunAfter(tasks.test)

    // build 태스크 실행 시에는 스킵
    onlyIf { !gradle.startParameter.taskNames.any { it.contains("build") } }
}

// E2E Test Resources - handle duplicates
tasks.named<Copy>("processE2eTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// E2E Test Task
val e2eTest by tasks.registering(Test::class) {
    description = "Runs E2E tests"
    group = "verification"
    // docker compose 로 띄운 로컬 인프라(local 프로필)에 e2e 전용 값을 얹는다
    systemProperty("spring.profiles.active", "local,e2e")
    // 브라우저(Playwright)·수동 탐색 테스트는 기본 제외 — 켜려면 -Pkotest.tags=browser
    systemProperty("kotest.tags", (findProperty("kotest.tags") ?: "!browser & !manual").toString())
    systemProperty("kotest.framework.config.fqn", "com.rex.api.pub.skeleton.config.KotestE2eProjectConfig")
    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")

    shouldRunAfter(integrationTest)

    // build 태스크 실행 시에는 스킵
    onlyIf { !gradle.startParameter.taskNames.any { it.contains("build") } }
}

// bootRun 은 SPRING_PROFILES_ACTIVE 가 없으면 local 로 뜬다 (docker compose up -d 선행)
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    if (System.getenv("SPRING_PROFILES_ACTIVE").isNullOrBlank()) {
        systemProperty("spring.profiles.active", "local")
    }
}

// e2eTest 는 docker compose 로 띄운 MySQL·Redis 가 필요해 check 에 묶지 않는다 — ./gradlew e2eTest 로 따로 실행한다
tasks.check {
    dependsOn(integrationTest)
}