package com.rex.api.pub.skeleton.convention

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * 트랜잭션 매니저 명시 규약 검사 (`.claude/rules/conventions.md` 「데이터베이스」 3).
 *
 * 데이터소스가 여럿이라 `transactionManager` 를 적지 않은 `@Transactional` 은 primary 로 묵시 바인딩된다 —
 * 다른 데이터소스의 리포지토리는 그 트랜잭션 밖에서 실행되어 `@Modifying` 은 `TransactionRequiredException`,
 * 인터페이스 파생 `deleteBy…` 는 조용히 버려지고, 읽기 뒤 쓰기는 커넥션을 둘 잡는다. Spring Context 없이 리플렉션으로 검사한다.
 */
class TransactionManagerConventionTest : BehaviorSpec({

    Given("service·persistence 계층의 @Transactional") {
        val targets = scan("service", "persistence.jpa.service", "persistence.jdbc.service")
        val classLevel = targets.mapNotNull { k -> findMergedAnnotation(k.java, Transactional::class.java)?.let { k to it } }
        val methodLevel = targets.flatMap { k ->
            k.java.declaredMethods
                .filter { !it.isSynthetic && !it.isBridge }
                .mapNotNull { m -> findMergedAnnotation(m, Transactional::class.java)?.let { Triple(k, m, it) } }
        }
        println("checked ${classLevel.size} class-level + ${methodLevel.size} method-level @Transactional")

        Then("T-1 모든 @Transactional 은 transactionManager 를 명시한다 (primary 포함)") {
            (classLevel.size + methodLevel.size) shouldBeGreaterThan 0
            val violations =
                classLevel.filter { it.second.transactionManager.isBlank() }.map { it.first.qualifiedName.orEmpty() } +
                    methodLevel.filter { it.third.transactionManager.isBlank() }.map { it.first.qualifiedName + "." + it.second.name }
            violations.forEach { println("T-1 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("T-2 private·final·static 메서드에는 @Transactional 이 없다 (프록시가 가로채지 못한다)") {
            methodLevel
                .filter { it.second.isNotProxyable() }
                .map { it.first.qualifiedName + "." + it.second.name }
                .shouldBeEmpty()
        }
    }

    Given("config.persistence 의 리포지토리 활성화") {
        val configs = scan("config.persistence")
        val refs = configs.flatMap { k ->
            listOfNotNull(
                findMergedAnnotation(k.java, EnableJpaRepositories::class.java)?.transactionManagerRef,
                findMergedAnnotation(k.java, EnableJdbcRepositories::class.java)?.transactionManagerRef,
            ).map { k.simpleName.orEmpty() to it }
        }
        val beans = configs.flatMap { k ->
            k.java.declaredMethods
                .filter { PlatformTransactionManager::class.java.isAssignableFrom(it.returnType) && it.isAnnotationPresent(Bean::class.java) }
                .map { m -> m.getAnnotation(Bean::class.java).let { b -> (b.value + b.name).firstOrNull() ?: m.name } }
        }.toSet()
        println("transactionManagerRef ${refs.size} / PlatformTransactionManager beans ${beans.size}: $beans")

        Then("T-3 transactionManagerRef 는 비어 있지 않고 같은 이름의 PlatformTransactionManager 빈이 있다") {
            refs.shouldNotBeEmpty()
            beans.shouldNotBeEmpty()
            refs.filter { it.second.isBlank() || it.second !in beans }.map { "${it.first} -> ${it.second}" }.shouldBeEmpty()
        }
    }

    Given("service.mapping") {
        Then("T-4 MappingService 에는 @Transactional 이 없다 (경계는 BizService·EntityService)") {
            val mapping = scan("service.mapping")
            mapping.shouldNotBeEmpty()
            mapping
                .filter { k ->
                    findMergedAnnotation(k.java, Transactional::class.java) != null ||
                        k.java.declaredMethods.any { findMergedAnnotation(it, Transactional::class.java) != null }
                }
                .map { it.qualifiedName.orEmpty() }
                .shouldBeEmpty()
        }
    }
}) {
    companion object {
        private const val BASE = "com.rex.api.pub.skeleton"

        /** kotlin.Metadata.kind — 1 = 클래스. 파일 파사드(XxxKt)·합성 클래스는 대상이 아니다. */
        private const val KOTLIN_CLASS_KIND = 1

        private fun Method.isNotProxyable(): Boolean =
            Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)

        private fun scan(vararg packages: String): List<KClass<*>> {
            val provider = ClassPathScanningCandidateComponentProvider(false).apply { addIncludeFilter { _, _ -> true } }
            return packages
                .flatMap { provider.findCandidateComponents("$BASE.$it") }
                .map { Class.forName(it.beanClassName).kotlin }
                .filter { k ->
                    !k.java.isInterface && !k.java.isEnum && !k.java.isAnnotation && k.objectInstance == null && !k.isCompanion && !k.isSealed &&
                        !k.java.isAnonymousClass && !k.java.isSynthetic &&
                        k.java.getAnnotation(Metadata::class.java)?.kind == KOTLIN_CLASS_KIND
                }
                .distinct()
        }
    }
}