package com.rex.api.pub.skeleton.convention

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.primaryConstructor

/**
 * 레이어드 아키텍처 규약 검사 (`.claude/rules/conventions.md` 「레이어드 아키텍처」).
 *
 * `Controller → MappingService → BizService → EntityService → Repository`. 계층 우회는 코드 리뷰로만 잡혀 왔고
 * 스켈레톤 샘플이 우회를 품으면 그것이 파생 프로젝트의 정본이 된다. 규칙마다 스캔 대상 수를 출력하고,
 * 레포별 최소 대상 수(`MIN`)를 먼저 단언해 스캐너·패키지 경로가 어긋났을 때 공허 통과하지 않는다(세 스켈레톤이 같은 파일을 공유한다).
 */
class LayeringConventionTest : BehaviorSpec({
    Given("controller 의 @Controller(@RestController 포함)") {
        val controllers = scan(Controller::class.java, "controller")
        println("L-1/L-4 checked ${controllers.size} (min ${MIN.controllers})")
        Then("L-0 컨트롤러 스캔 대상이 최소 수 이상이다 (스캐너 고장으로 공허 통과하지 않는다)") {
            controllers.size shouldBeGreaterThanOrEqual MIN.controllers
        }

        Then("L-1 생성자 주입은 MappingService 와 요청 경계 보조(config·util 패키지, 프레임워크 타입)만 — BizService·EntityService·Repository·Client 등 그 외는 위반") {
            val violations =
                controllers.flatMap { k ->
                    k.constructorParameterClassifiers()
                        .filterNot { it.isAllowedInController() }
                        .map { "${k.simpleName}: ${it.simpleName}" }
                }
            violations.forEach { println("L-1 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("L-4 public 메서드(@ExceptionHandler 포함)의 반환 타입과 제네릭 인자에 Dto·Entity 가 없다 (응답은 Model)") {
            val violations =
                controllers.flatMap { k ->
                    k.publicDeclaredFunctions().flatMap { f ->
                        f.returnType.classifierNames()
                            .filter { it.endsWithAny("Dto", "Entity") && it !in HTTP_WRAPPERS }
                            .map { "${k.simpleName}.${f.name}: $it" }
                    }
                }
            violations.forEach { println("L-4 violation: $it") }
            violations.shouldBeEmpty()
        }
    }

    Given("service.mapping 의 @Service") {
        val mappings = scan(Service::class.java, "service.mapping")
        println("L-2 checked ${mappings.size} (min ${MIN.mappings})")
        Then("L-0 MappingService 스캔 대상이 최소 수 이상이다") {
            mappings.size shouldBeGreaterThanOrEqual MIN.mappings
        }

        Then("L-2 MappingService 는 BizService·Mapper·Properties 만 주입받는다") {
            val violations =
                mappings.flatMap { k ->
                    k.constructorParameterTypes()
                        .filterNot { it.endsWithAny("BizService", "Mapper", "Properties") }
                        .map { "${k.simpleName}: $it" }
                }
            violations.forEach { println("L-2 violation: $it") }
            violations.shouldBeEmpty()
        }
    }

    Given("service·persistence 의 @Service") {
        val services = scan(Service::class.java, "service", "persistence.jpa.service", "persistence.jdbc.service")
        val bizServices = services.filter { it.simpleName.orEmpty().endsWith("BizService") }
        val entityServices = services.filter { it.simpleName.orEmpty().endsWith("EntityService") }
        println("L-3 checked ${bizServices.size} (min ${MIN.bizServices}) / L-5 checked ${entityServices.size} (min ${MIN.entityServices})")
        Then("L-0 BizService·EntityService 스캔 대상이 최소 수 이상이다") {
            bizServices.size shouldBeGreaterThanOrEqual MIN.bizServices
            entityServices.size shouldBeGreaterThanOrEqual MIN.entityServices
        }

        Then("L-3 BizService 는 Repository 를 직접 주입하지 않는다 (EntityService 위에 선다)") {
            val violations =
                bizServices.flatMap { k ->
                    k.constructorParameterTypes().filter { it.endsWith("Repository") }.map { "${k.simpleName}: $it" }
                }
            violations.forEach { println("L-3 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("L-5 EntityService 의 public 메서드는 Entity 를 받지도 돌려주지도 않는다 (DTO 또는 의도 메서드)") {
            val violations =
                entityServices.flatMap { k ->
                    k.publicDeclaredFunctions().flatMap { f ->
                        val types = f.returnType.classifierNames() + f.parameters.drop(1).flatMap { it.type.classifierNames() }
                        types.filter { it.endsWith("Entity") && it !in HTTP_WRAPPERS }.map { "${k.simpleName}.${f.name}: $it" }
                    }
                }
            violations.forEach { println("L-5 violation: $it") }
            violations.shouldBeEmpty()
        }
    }
}) {
    companion object {
        /** 테스트 클래스의 패키지에서 base package 를 얻는다 — 세 스켈레톤이 이 파일을 글자 그대로 공유한다. */
        private val BASE = LayeringConventionTest::class.java.packageName.removeSuffix(".convention")

        /** 레포 식별자 — base package 의 `.skeleton` 앞 세그먼트(`pub`·`internal`·`batch`). 세 스켈레톤이 같은 파일을 쓰므로 base package 문자열은 여기 두지 않는다. */
        private val REPO = BASE.removeSuffix(".skeleton").substringAfterLast('.')

        /** 레포별 최소 스캔 대상 수 — 대상이 이보다 적으면 규약 위반이 아니라 스캐너가 잘못된 것이다(공허 통과 방지). */
        private data class MinTargets(val controllers: Int, val mappings: Int, val bizServices: Int, val entityServices: Int)

        private val MIN =
            mapOf(
                "pub" to MinTargets(controllers = 10, mappings = 10, bizServices = 15, entityServices = 10),
                "internal" to MinTargets(controllers = 1, mappings = 2, bizServices = 3, entityServices = 3),
                "batch" to MinTargets(controllers = 2, mappings = 2, bizServices = 3, entityServices = 8),
            ).getValue(REPO)

        /** kotlin.Metadata.kind — 1 = 클래스. 파일 파사드(XxxKt)·합성 클래스는 대상이 아니다. */
        private const val KOTLIN_CLASS_KIND = 1

        /** 이름이 `Entity` 로 끝나지만 JPA Entity 가 아닌 Spring HTTP 래퍼 — 접미사 검사에서 제외한다. */
        private val HTTP_WRAPPERS = setOf("ResponseEntity", "HttpEntity", "RequestEntity")

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

        private fun KClass<*>.constructorParameterTypes(): List<String> =
            primaryConstructor?.parameters?.flatMap { it.type.classifierNames() } ?: emptyList()

        private fun KClass<*>.constructorParameterClassifiers(): List<KClass<*>> =
            primaryConstructor?.parameters?.mapNotNull { it.type.classifier as? KClass<*> } ?: emptyList()

        /**
         * 컨트롤러가 주입받아도 되는 것 — MappingService 와 요청 경계 보조(`config.**`·`util.**` 의 컴포넌트: Properties·Resolver·Authorizer 류)만.
         * 프레임워크 타입(WebClient·JsonMapper·리포지토리 등)도 전부 위반이다 — 컨트롤러는 HTTP 요청·응답 변환 밖의 의존을 갖지 않는다(허용 목록형).
         */
        private fun KClass<*>.isAllowedInController(): Boolean {
            val name = qualifiedName.orEmpty()
            return name.endsWith("MappingService") || name.startsWith("$BASE.config.") || name.startsWith("$BASE.util.")
        }

        private fun KClass<*>.publicDeclaredFunctions(): List<KFunction<*>> =
            declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }

        /** 타입과 제네릭 인자를 재귀로 펼친 classifier 단순 이름 — `ResponseEntity<List<XDto>>` → [ResponseEntity, List, XDto]. */
        private fun KType.classifierNames(): List<String> =
            listOfNotNull((classifier as? KClass<*>)?.simpleName) + arguments.flatMap { it.type?.classifierNames() ?: emptyList() }

        private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any { endsWith(it) }
    }
}