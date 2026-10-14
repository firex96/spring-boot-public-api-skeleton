package com.rex.api.pub.skeleton.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Testcontainers 설정.
 *
 * Docker 컨테이너를 사용하여 실제 데이터베이스 및 Redis와 통합 테스트를 수행합니다.
 * - MySQL: main(업무 테이블은 db/manual/create-main.sql), auth(Flyway) 데이터소스
 * - Redis(standalone): 캐시 및 세션 저장소
 *
 * 사용 예시:
 * ```kotlin
 * @SpringBootTest
 * @Import(TestcontainersConfiguration::class)
 * @ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
 * class MyDatabaseIntegrationTest {
 *     // 테스트 코드
 * }
 * ```
 *
 * 주의사항:
 * - Docker가 설치되어 있어야 합니다
 * - 테스트 실행 시 컨테이너 시작으로 인한 초기 지연이 있습니다
 * - CI/CD 환경에서는 Docker-in-Docker 또는 Docker socket 마운트가 필요합니다
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    companion object {
        private const val MYSQL_IMAGE = "mysql:8.0"
        private const val REDIS_IMAGE = "redis:7-alpine"
        private const val REDIS_CONTAINER_PORT = 6379

        /**
         * Redis 컨테이너의 호스트 포트.
         *
         * **고정 포트가 필요하다.** 클러스터 노드는 `cluster-announce-port` 로 광고한 주소를
         * 토폴로지에 싣고, 클라이언트는 그 주소로 재접속한다. 컨테이너 기동 전에 포트를 알아야
         * announce 값을 줄 수 있으므로 비어 있는 포트를 먼저 잡아 고정 바인딩한다.
         */
        private val redisHostPort: Int by lazy { findFreePortForCluster() }

        /**
         * 클러스터 버스 포트(= 포트 + 10000)가 65535 를 넘지 않는 범위에서 빈 포트를 찾는다.
         *
         * `ServerSocket(0)` 이 주는 임시 포트는 보통 49152~65535 라 +10000 이 범위를 넘고,
         * 그러면 redis 가 기동하지 못한다.
         */
        private fun findFreePortForCluster(): Int {
            for (candidate in 20_000..40_000) {
                runCatching {
                    java.net.ServerSocket(candidate).close()
                    java.net.ServerSocket(candidate + 10_000).close()
                }.onSuccess { return candidate }
            }
            error("Redis 클러스터용 빈 포트를 찾지 못했다 (20000~40000)")
        }

        /**
         * MySQL 테스트 컨테이너 (main·auth 데이터소스용).
         */
        val mainMysqlContainer: MySQLContainer<*> by lazy {
            MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))
                .withDatabaseName("skeleton_main")
                .withInitScript("db/manual/create-main.sql")
                .withUsername("test")
                .withPassword("test")
                .also { it.start() }
        }

        val authMysqlContainer: MySQLContainer<*> by lazy {
            MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))
                .withDatabaseName("skeleton_auth")
                .withUsername("test")
                .withPassword("test")
                .also { it.start() }
        }

        /**
         * Redis 테스트 컨테이너 (캐시 저장소).
         *
         * **관리형 Redis 와 같은 모양으로 띄운다** — 관리형 Redis(클러스터 모드)는 클러스터 프로토콜을
         * 쓰면서 엔드포인트가 하나인 경우가 많다(`cluster.nodes` 에 1개).
         * 그래서 6노드 이미지가 아니라 **모든 슬롯을 가진 단일 노드 클러스터**를 띄운다.
         *
         * `cluster-announce-*` 로 호스트에서 도달 가능한 주소를 광고해야 한다. 광고 주소가
         * 컨테이너 내부 주소면 클라이언트가 토폴로지를 받은 뒤 재접속에서 거부된다.
         *
         * **TLS 는 걸지 않는다** — 관리형 Redis 는 보통 TLS 를 쓰지만, 로컬 컨테이너에 인증서를 붙이는
         * 비용이 이 테스트가 얻는 것보다 크다. 그래서 테스트는 Boot 자동 구성의 클러스터 팩토리를 쓰고
         * `RedisConfiguration`(`app.redis.managed-cluster=true` 일 때만 켜짐)은 쓰지 않는다.
         */
        val redisContainer: GenericContainer<*> by lazy {
            GenericContainer(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_CONTAINER_PORT)
                .withCommand(
                    "redis-server",
                    "--cluster-enabled", "yes",
                    "--cluster-config-file", "nodes.conf",
                    "--cluster-announce-ip", "127.0.0.1",
                    "--cluster-announce-port", "$redisHostPort",
                    "--cluster-announce-bus-port", "${redisHostPort + 10000}",
                )
                .also { container ->
                    container.portBindings = listOf("$redisHostPort:$REDIS_CONTAINER_PORT")
                    container.start()
                    formSingleNodeCluster(container)
                }
        }

        /**
         * 단일 노드에 전 슬롯을 할당해 클러스터를 형성한다.
         *
         * `cluster_state:ok` 가 될 때까지 기다린다 — 슬롯 할당 직후에는 아직 `fail` 이다.
         */
        private fun formSingleNodeCluster(container: GenericContainer<*>) {
            container.execInContainer("redis-cli", "cluster", "addslotsrange", "0", "16383")
            repeat(30) {
                val info = container.execInContainer("redis-cli", "cluster", "info").stdout
                if (info.contains("cluster_state:ok")) return
                Thread.sleep(500)
            }
            error("Redis 단일 노드 클러스터가 cluster_state:ok 에 도달하지 못했다")
        }
    }

    /**
     * ApplicationContextInitializer for configuring datasources from Testcontainers.
     * Use with @ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
     */
    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            // Start containers
            val main = mainMysqlContainer
            val authMysql = authMysqlContainer
            // Redis 도 시작 (lazy이므로 여기서 접근해야 시작됨)
            val redis = redisContainer

            TestPropertyValues.of(
                "spring.flyway.enabled=true",
                "app.flyway.mysql.username=${authMysql.username}",
                "app.flyway.mysql.password=${authMysql.password}",
                // main datasource (MySQL)
                "spring.datasource.main.read.url=${main.jdbcUrl}",
                "spring.datasource.main.read.username=${main.username}",
                "spring.datasource.main.read.password=${main.password}",
                "spring.datasource.main.write.url=${main.jdbcUrl}",
                "spring.datasource.main.write.username=${main.username}",
                "spring.datasource.main.write.password=${main.password}",
                // Auth datasource (MySQL)
                "spring.datasource.auth.read.url=${authMysql.jdbcUrl}",
                "spring.datasource.auth.read.username=${authMysql.username}",
                "spring.datasource.auth.read.password=${authMysql.password}",
                "spring.datasource.auth.write.url=${authMysql.jdbcUrl}",
                "spring.datasource.auth.write.username=${authMysql.username}",
                "spring.datasource.auth.write.password=${authMysql.password}",
                // Redis 설정 — 관리형 Redis 와 같은 단일 엔드포인트 클러스터
                "spring.data.redis.cluster.nodes=127.0.0.1:$redisHostPort",
                "spring.data.redis.password=",
                "spring.data.redis.ssl.enabled=false",
            ).applyTo(applicationContext.environment)
        }
    }

    @Bean
    fun mainMysqlContainer(): MySQLContainer<*> = Companion.mainMysqlContainer

    @Bean
    fun authMysqlContainer(): MySQLContainer<*> = Companion.authMysqlContainer

    @Bean
    fun redisContainer(): GenericContainer<*> = Companion.redisContainer
}