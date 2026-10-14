package com.rex.api.pub.skeleton.convention

import com.fasterxml.jackson.annotation.JsonProperty
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod

/**
 * 타입 선언 규약(`.claude/rules/conventions.md` 「타입 선언」)을 리플렉션으로 검사한다.
 *
 * - D-1 Model·DTO·Response·Spring Data JDBC `*JdbcEntity` — `data class` + `val`(`var` 없음) + 전부-기본값 아님
 *   (전부 기본값이면 no-arg 생성자가 생겨 MapStruct 가 setter 경로로 떨어져 빈 객체를 만든다).
 * - D-2 JPA `@Entity`·`@MappedSuperclass` — `data class` 금지 + 프로퍼티 전부 `var`(no-arg 생성자 + setter 로 채워진다).
 * - D-3 `@Column(nullable = false)` 프로퍼티는 non-null Kotlin 타입 — nullable 은 DB 컬럼 NULL 허용과 일치한다.
 * - D-4 Model 의 모든 프로퍼티에 `@field:JsonProperty` 가 있고 값은 프로퍼티명과 같다(외부 계약 snake_case 는 명시 예외 목록만).
 *
 * `spring-core` 의 클래스패스 스캐너와 `kotlin-reflect` 만 쓴다 — Spring 컨텍스트는 만들지 않는다.
 */
class DeclarationStyleConventionTest : BehaviorSpec({

    Given("dto · model · response · persistence.jdbc.entity 패키지의 클래스") {
        val targets = scan("dto", "model", "client.core.response", "persistence.jdbc.entity")

        Then("D-1 data class + val 이고 생성자 파라미터 전부에 기본값이 있지 않다") {
            targets.shouldNotBeEmpty()
            println("checked ${targets.size} classes (dto/model/response/jdbc entity)")
            targets.filter { !it.isData }.map { "${it.qualifiedName}: data class 가 아니다" }.shouldBeEmpty()
            targets.filter { k -> k.declaredMemberProperties.any { it is KMutableProperty1 } }
                .map { "${it.qualifiedName}: var 프로퍼티 — @MappingTarget 갱신 매핑이 아니면 val" }.shouldBeEmpty()
            targets.filter { k -> k.primaryConstructor?.parameters?.let { ps -> ps.isNotEmpty() && ps.all { it.isOptional } } == true }
                .map { "${it.qualifiedName}: 생성자 파라미터 전부 기본값 — MapStruct 가 setter 경로로 떨어져 빈 객체를 만든다(@Default 생성자 또는 필수 파라미터)" }
                .shouldBeEmpty()
        }
    }

    Given("persistence.jpa.entity 패키지의 @Entity · @MappedSuperclass") {
        val entities =
            scan("persistence.jpa.entity").filter { it.java.isAnnotationPresent(Entity::class.java) || it.java.isAnnotationPresent(MappedSuperclass::class.java) }

        Then("D-2 class + var — data class 금지, 프로퍼티는 전부 var") {
            entities.shouldNotBeEmpty()
            println("checked ${entities.size} entities")
            entities.filter { it.isData }.map { "${it.qualifiedName}: JPA 엔티티는 data class 금지" }.shouldBeEmpty()
            entities.flatMap { k -> k.declaredMemberProperties.filter { it !is KMutableProperty1 }.map { "${k.qualifiedName}.${it.name}: JPA 엔티티 프로퍼티는 var" } }
                .shouldBeEmpty()
        }

        Then("D-3 @Column(nullable = false) 프로퍼티는 non-null 타입이다") {
            var checked = 0
            entities.flatMap { k ->
                k.declaredMemberProperties.mapNotNull { p ->
                    val column = p.columnAnnotation() ?: return@mapNotNull null
                    if (column.nullable) return@mapNotNull null
                    checked++
                    if (p.returnType.isMarkedNullable) "${k.qualifiedName}.${p.name}: @Column(nullable = false) 인데 Kotlin 타입이 nullable" else null
                }
            }.shouldBeEmpty()
            println("checked $checked non-null columns")
        }
    }

    Given("model 패키지의 클래스") {
        val models = scan("model")

        Then("D-4 모든 프로퍼티에 @field:JsonProperty 가 있고 값이 프로퍼티명과 같다(외부 계약 snake_case 는 예외 목록만)") {
            models.shouldNotBeEmpty()
            var checked = 0
            models.flatMap { k ->
                k.declaredMemberProperties.mapNotNull { p ->
                    checked++
                    val value = p.javaField?.getAnnotation(JsonProperty::class.java)?.value
                    when {
                        value == null -> "${k.qualifiedName}.${p.name}: @field:JsonProperty 없음"
                        value.isBlank() -> "${k.qualifiedName}.${p.name}: @field:JsonProperty 값이 비어 있다"
                        value != p.name && k.simpleName !in SNAKE_CASE_CONTRACT ->
                            "${k.qualifiedName}.${p.name}: @field:JsonProperty(\"$value\") 가 프로퍼티명과 다르다(외부 계약이면 SNAKE_CASE_CONTRACT 에 등록)"
                        else -> null
                    }
                }
            }.shouldBeEmpty()
            println("checked $checked model properties")
        }
    }
}) {
    companion object {
        private const val BASE = "com.rex.api.pub.skeleton"

        /** kotlin.Metadata.kind — 1 = 클래스. 2(파일 파사드)·3(합성)·4/5(멀티파일) 는 선언 규약의 대상이 아니다. */
        private const val KOTLIN_CLASS_KIND = 1

        /** 외부 계약(OIDC Discovery·UserInfo 표준 필드명)이라 snake_case 를 쓰는 Model — 그 외 Model 은 프로퍼티명과 같아야 한다. */
        private val SNAKE_CASE_CONTRACT = setOf("OpenIdConfigurationModel", "UserInfoModel", "OidcTokenResponseModel") // OIDC Discovery · userinfo · 토큰 응답(RFC 6749 §5.1)

        private fun scan(vararg packages: String): List<KClass<*>> {
            val provider = ClassPathScanningCandidateComponentProvider(false).apply { addIncludeFilter { _, _ -> true } }
            return packages
                .flatMap { provider.findCandidateComponents("$BASE.$it") }
                .map { Class.forName(it.beanClassName).kotlin }
                .filter { k ->
                    !k.java.isInterface && !k.java.isEnum && !k.java.isAnnotation && k.objectInstance == null && !k.isCompanion && !k.isSealed &&
                        !k.java.isAnonymousClass && !k.java.isSynthetic &&
                        k.java.getAnnotation(Metadata::class.java)?.kind == KOTLIN_CLASS_KIND // 파일 파사드(XxxKt)·합성 클래스 제외
                }
                .distinct()
        }

        /** Kotlin 은 use-site 를 안 적으면 Java 애노테이션을 field 에 붙인다 — field 우선, 없으면 getter. */
        private fun KProperty1<*, *>.columnAnnotation(): Column? =
            javaField?.getAnnotation(Column::class.java) ?: getter.javaMethod?.getAnnotation(Column::class.java)
    }
}