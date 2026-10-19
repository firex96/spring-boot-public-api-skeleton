package com.rex.api.pub.skeleton.config.secret

import com.azure.core.credential.BasicAuthenticationCredential
import com.azure.core.credential.TokenCredential
import com.azure.security.keyvault.secrets.SecretClientBuilder
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainerBuilder.lowkeyVault
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.WebApplicationType
import org.springframework.boot.bootstrap.BootstrapRegistry
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.testcontainers.utility.DockerImageName

/**
 * azure 프로필(`application-azure.yml`)이 Key Vault 의 같은 이름 비밀로 기본 yml 의 논리 키를 모두 채우는지 확인한다.
 *
 * Key Vault 에뮬레이터(Lowkey Vault)에 비밀을 만들고 빈 설정 클래스로 기동한다 — 검증 대상은 설정 로딩 경로(Key Vault 속성 소스 →
 * 플레이스홀더 해석 → UnresolvedPlaceholderGuard)다. 실제 Azure 인증은 검증하지 않는다: 부트스트랩 컨텍스트에 등록한
 * 테스트 자격증명을 Spring Cloud Azure 가 우선 쓰므로 개발자 머신의 `az login` 세션이 끼어들지 않는다.
 */
class AzureKeyVaultProfileIntegrationTest : StringSpec({
    // mergeTrustStores — 에뮬레이터의 자체 서명 인증서를 JVM 신뢰 저장소에 합친다(stop 이 원래대로 되돌린다).
    // 앱의 TLS 컨텍스트가 만들어지기 전에 시작해야 하므로 beforeSpec 에서 띄운다.
    val vault = lowkeyVault(DockerImageName.parse(LOWKEY_VAULT_IMAGE)).mergeTrustStores().build()

    beforeSpec { vault.start() }
    afterSpec { vault.stop() }

    "azure 프로필은 Key Vault 비밀로 기본 yml 의 논리 키를 해석한다" {
        val credential = BasicAuthenticationCredential(vault.username, vault.password)
        val secretClient =
            SecretClientBuilder()
                .vaultUrl(vault.defaultVaultBaseUrl)
                .disableChallengeResourceVerification()
                .credential(credential)
                .buildClient()
        mapOf(
            "db-host" to "db.example.internal",
            "db-reader-username" to "reader",
            "db-reader-password" to "reader-from-azure",
            "db-writer-username" to "writer",
            "db-writer-password" to "writer-from-azure",
            "db-admin-username" to "admin",
            "db-admin-password" to "admin-from-azure",
            "redis-host" to "redis.example.internal",
            "redis-password" to "redis-from-azure",
            "core-api-url" to "http://core.example.internal",
            "jwt-secret" to "jwt-secret-from-azure-must-be-at-least-32-chars",
            "mfa-secret-encryption-key" to "mfa-key-from-azure",
            "oidc-issuer" to "https://auth.example.internal",
            "oauth2-redirect-base-url" to "https://api.example.internal",
        ).forEach { (name, value) -> secretClient.setSecret(name, value) }

        SpringApplicationBuilder(EmptyConfiguration::class.java)
            .web(WebApplicationType.NONE)
            .addBootstrapRegistryInitializer {
                it.register(TokenCredential::class.java, BootstrapRegistry.InstanceSupplier.of(credential))
            }.run(
                // 명령줄 인수가 integrationTest 의 기본 프로필(testcontainers) 시스템 속성보다 우선한다 — azure 만 켠다
                "--spring.profiles.active=azure",
                "--AZURE_KEYVAULT_ENDPOINT=${vault.defaultVaultBaseUrl}",
                // property-sources[0] 는 목록이라 출처 간에 합쳐지지 않는다 — 항목 키를 직접 주면 yml 의 endpoint 가 가려지므로 환경변수로 넘긴다
                "--AZURE_KEYVAULT_CHALLENGE_RESOURCE_VERIFICATION_ENABLED=false",
            ).use { context ->
                val env = context.environment
                env.getProperty("spring.datasource.main.write.password") shouldBe "writer-from-azure"
                env.getProperty("spring.datasource.auth.read.username") shouldBe "reader"
                env.getProperty("spring.datasource.main.write.url") shouldBe "jdbc:mysql://db.example.internal:3306/skeleton_main"
                env.getProperty("spring.data.redis.password") shouldBe "redis-from-azure"
                env.getProperty("app.client.destinations.core.url") shouldBe "http://core.example.internal"
                env.getProperty("app.flyway.mysql.password") shouldBe "admin-from-azure"
                env.getProperty("jwt.secret") shouldBe "jwt-secret-from-azure-must-be-at-least-32-chars"
                env.getProperty("mfa.secret-encryption-key") shouldBe "mfa-key-from-azure"
                env.getProperty("oidc.issuer") shouldBe "https://auth.example.internal"
            }
    }
}) {
    @Configuration(proxyBeanMethods = false)
    class EmptyConfiguration

    companion object {
        // -ubi10-minimal 태그가 arm64·amd64 를 함께 지원한다
        private const val LOWKEY_VAULT_IMAGE = "nagyesta/lowkey-vault:7.3.127-ubi10-minimal"
    }
}