package com.rex.api.pub.skeleton.convention

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import kotlin.reflect.KClass

/**
 * 클래스 접미사 규약 검사 (README 「코딩 규약」 접미사 표).
 *
 * `service/`·`persistence/**/service/` 의 `@Service` 는 `BizService`·`MappingService`·`EntityService` 로만 끝난다 —
 * 맨 `Service` 접미사는 계층을 숨긴다(컨트롤러가 BizService 를 직접 부르는지, MappingService 를 부르는지 이름으로 구별되지 않는다).
 * 외부 API 어댑터는 `client/` 의 `~Client`, 컨트롤러는 `~Controller`. Spring Context 없이 클래스패스 스캔으로 검사한다.
 * 규칙마다 레포별 최소 대상 수(`MIN`)를 별도 케이스(N-0)로 단언해 스캐너·패키지 경로가 어긋났을 때 공허 통과하지 않는다 —
 * 별도 케이스라 정정 전 코드에서도 N-1~N-3 의 위반 목록·카운트는 그대로 출력된다(세 스켈레톤이 이 파일을 글자 그대로 공유한다).
 */
class ServiceNamingConventionTest : BehaviorSpec({
    Given("service·persistence 계층의 @Service") {
        val targets = scan(Service::class.java, "service", "persistence.jpa.service", "persistence.jdbc.service")
        println("N-1 checked ${targets.size} (min ${MIN.services})")
        Then("N-0 service 스캔 대상이 최소 수 이상이다 (스캐너·패키지 경로가 어긋나 대상이 줄면 여기서 실패한다)") {
            targets.size shouldBeGreaterThanOrEqual MIN.services
        }

        Then("N-1 이름은 BizService·MappingService·EntityService 로 끝난다") {
            val violations =
                targets.map { it.qualifiedName.orEmpty() }
                    .filterNot { it.endsWith("BizService") || it.endsWith("MappingService") || it.endsWith("EntityService") }
            violations.forEach { println("N-1 violation: $it") }
            violations.shouldBeEmpty()
        }
    }

    Given("client 패키지의 @Component(@Service 포함)") {
        val targets = scan(Component::class.java, "client")
        println("N-2 checked ${targets.size} (min ${MIN.clients})")
        Then("N-0 client 스캔 대상이 최소 수 이상이다 (client 패키지가 없는 레포만 최소 0)") {
            targets.size shouldBeGreaterThanOrEqual MIN.clients
        }

        Then("N-2 이름은 Client 로 끝난다") {
            val violations = targets.map { it.qualifiedName.orEmpty() }.filterNot { it.endsWith("Client") }
            violations.forEach { println("N-2 violation: $it") }
            violations.shouldBeEmpty()
        }
    }

    Given("controller 패키지의 @Controller(@RestController 포함)") {
        val targets = scan(Controller::class.java, "controller")
        println("N-3 checked ${targets.size} (min ${MIN.controllers})")
        Then("N-0 controller 스캔 대상이 최소 수 이상이다") {
            targets.size shouldBeGreaterThanOrEqual MIN.controllers
        }

        Then("N-3 이름은 Controller 로 끝난다") {
            val violations = targets.map { it.qualifiedName.orEmpty() }.filterNot { it.endsWith("Controller") }
            violations.forEach { println("N-3 violation: $it") }
            violations.shouldBeEmpty()
        }
    }
}) {
    companion object {
        /** 테스트 클래스의 패키지에서 base package 를 얻는다 — 세 스켈레톤이 이 파일을 글자 그대로 공유한다. */
        private val BASE = ServiceNamingConventionTest::class.java.packageName.removeSuffix(".convention")

        /** 레포 식별자 — base package 의 `.skeleton` 앞 세그먼트(`pub`·`internal`·`batch`). 세 스켈레톤이 같은 파일을 쓰므로 base package 문자열은 여기 두지 않는다. */
        private val REPO = BASE.removeSuffix(".skeleton").substringAfterLast('.')

        /** 레포별 최소 스캔 대상 수 — 대상이 이보다 적으면 규약 위반이 아니라 스캐너가 잘못된 것이다(공허 통과 방지). */
        private data class MinTargets(val services: Int, val clients: Int, val controllers: Int)

        private val MIN =
            mapOf(
                "pub" to MinTargets(services = 40, clients = 5, controllers = 10),
                "internal" to MinTargets(services = 8, clients = 2, controllers = 1),
                "batch" to MinTargets(services = 12, clients = 0, controllers = 2),
            ).getValue(REPO)

        /** kotlin.Metadata.kind — 1 = 클래스. 파일 파사드(XxxKt)·합성 클래스는 대상이 아니다. */
        private const val KOTLIN_CLASS_KIND = 1

        private fun scan(annotation: Class<out Annotation>, vararg packages: String): List<KClass<*>> {
            val provider = ClassPathScanningCandidateComponentProvider(false).apply { addIncludeFilter(AnnotationTypeFilter(annotation)) }
            return packages
                .flatMap { provider.findCandidateComponents("$BASE.$it") }
                .map { Class.forName(it.beanClassName).kotlin }
                .filter { k ->
                    !k.java.isInterface && !k.isAbstract && !k.java.isAnonymousClass && !k.java.isSynthetic &&
                        k.java.getAnnotation(Metadata::class.java)?.kind == KOTLIN_CLASS_KIND
                }
                .distinct()
        }
    }
}