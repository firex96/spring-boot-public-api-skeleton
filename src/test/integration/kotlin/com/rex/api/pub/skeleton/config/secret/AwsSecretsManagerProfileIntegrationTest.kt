package com.rex.api.pub.skeleton.config.secret

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient

/**
 * aws 프로필(`application-aws.yml`)이 Secrets Manager 의 JSON 비밀 하나로 기본 yml 의 논리 키를 모두 채우는지 확인한다.
 *
 * 애플리케이션 전체가 아니라 빈 설정 클래스로 기동한다 — 검증 대상은 설정 로딩 경로(config import → 플레이스홀더 해석 →
 * UnresolvedPlaceholderGuard)이고, 데이터소스·Redis 연결은 이 테스트의 관심이 아니다. LocalStack 으로 실제 API 를 호출한다.
 */
class AwsSecretsManagerProfileIntegrationTest : StringSpec({
    val localstack =
        LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE))
            .withServices("secretsmanager")

    beforeSpec { localstack.start() }
    afterSpec { localstack.stop() }

    "aws 프로필은 Secrets Manager 비밀의 키로 기본 yml 의 논리 키를 해석한다" {
        SecretsManagerClient
            .builder()
            .endpointOverride(localstack.endpoint)
            .region(Region.of(localstack.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)),
            ).build()
            .use { client ->
                client.createSecret {
                    it.name(SECRET_ID).secretString(
                        """
                        {
                          "db-host": "db.example.internal",
                          "db-reader-username": "reader",
                          "db-reader-password": "reader-from-aws",
                          "db-writer-username": "writer",
                          "db-writer-password": "writer-from-aws",
                          "db-admin-username": "admin",
                          "db-admin-password": "admin-from-aws",
                          "redis-host": "redis.example.internal",
                          "redis-password": "redis-from-aws",
                          "core-api-url": "http://core.example.internal",
                          "jwt-secret": "jwt-secret-from-aws-must-be-at-least-32-chars",
                          "mfa-secret-encryption-key": "mfa-key-from-aws",
                          "oidc-issuer": "https://auth.example.internal",
                          "oauth2-redirect-base-url": "https://api.example.internal"
                        }
                        """.trimIndent(),
                    )
                }
            }

        SpringApplicationBuilder(EmptyConfiguration::class.java)
            .web(WebApplicationType.NONE)
            .run(
                // 명령줄 인수가 integrationTest 의 기본 프로필(testcontainers) 시스템 속성보다 우선한다 — aws 만 켠다
                "--spring.profiles.active=aws",
                "--AWS_SECRET_ID=$SECRET_ID",
                "--spring.cloud.aws.endpoint=${localstack.endpoint}",
                "--spring.cloud.aws.region.static=${localstack.region}",
                "--spring.cloud.aws.credentials.access-key=${localstack.accessKey}",
                "--spring.cloud.aws.credentials.secret-key=${localstack.secretKey}",
            ).use { context ->
                val env = context.environment
                env.getProperty("spring.datasource.main.write.password") shouldBe "writer-from-aws"
                env.getProperty("spring.datasource.auth.read.username") shouldBe "reader"
                env.getProperty("spring.datasource.main.write.url") shouldBe "jdbc:mysql://db.example.internal:3306/skeleton_main"
                env.getProperty("spring.data.redis.password") shouldBe "redis-from-aws"
                env.getProperty("app.client.destinations.core.url") shouldBe "http://core.example.internal"
                env.getProperty("app.flyway.mysql.password") shouldBe "admin-from-aws"
                env.getProperty("jwt.secret") shouldBe "jwt-secret-from-aws-must-be-at-least-32-chars"
                env.getProperty("mfa.secret-encryption-key") shouldBe "mfa-key-from-aws"
                env.getProperty("oidc.issuer") shouldBe "https://auth.example.internal"
            }
    }
}) {
    @Configuration(proxyBeanMethods = false)
    class EmptyConfiguration

    companion object {
        private const val LOCALSTACK_IMAGE = "localstack/localstack:4.4.0"
        private const val SECRET_ID = "public-api-skeleton/test"
    }
}