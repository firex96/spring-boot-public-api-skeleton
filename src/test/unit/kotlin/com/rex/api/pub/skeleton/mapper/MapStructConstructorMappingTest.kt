package com.rex.api.pub.skeleton.mapper

import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import io.github.classgraph.ScanResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldEndWith
import jakarta.persistence.Entity
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction
import kotlin.streams.asSequence

/**
 * MapStruct 가 DTO·Model 을 **생성자 매핑**으로 만드는지, 그리고 `unmappedTargetPolicy = ERROR` 아래에서
 * non-null 타깃이 조용히 `null`/기본값으로 채워질 길이 없는지 확인한다.
 *
 * 규약(`.claude/rules/conventions.md` 「타입 선언」):
 * - M-1·M-2 — 전부 기본값이면 Kotlin 이 파라미터 없는 생성자를 만들고 MapStruct 는 setter 경로로 떨어져 **빈 객체**를 만든다.
 *   생성된 `*MapperImpl.java` 에 `new XDto();`·`new XModel();` 서명이 없어야 한다.
 * - M-3 — `MapstructConfiguration.unmappedTargetPolicy = ERROR`.
 * - M-4 — `@Mapping(ignore = true)` 는 nullable 타깃과 `@Id` 에만. JPA Entity 도 예외가 아니다(no-arg 생성자가 초기화식을 실행하지 않는다).
 * - M-5 — nullable 소스 → non-null 타깃은 `expression`·`defaultValue`·`defaultExpression`, 또는 non-null 을 반환하는 `@Named` 로만 닫는다.
 *
 * MapStruct 애노테이션은 `RetentionPolicy.CLASS` 라 런타임 리플렉션으로 보이지 않는다 — ClassGraph 로 클래스 파일의
 * invisible 애노테이션을 읽고, 타입·nullability 는 kotlin-reflect 로 본다.
 */
class MapStructConstructorMappingTest : BehaviorSpec({

    Given("kapt 가 생성한 MapperImpl 소스가 있을 때") {
        val generated = Paths.get("build/generated/source/kapt/main")
        val impls =
            Files.walk(generated).use { stream ->
                stream.asSequence().filter { it.name.endsWith("MapperImpl.java") }.toList()
            }

        Then("M-1 검사할 MapperImpl 이 하나 이상 있다 — 검사 대상이 없어서 통과하는 상태를 막는다") {
            impls.shouldNotBeEmpty()
            println("checked ${impls.size} MapperImpl files")
        }

        Then("M-2 DTO·Model 을 파라미터 없는 생성자로 만드는 매퍼가 없다") {
            val offenders =
                impls.flatMap { path ->
                    Files.readAllLines(path).mapIndexedNotNull { index, line ->
                        if (SETTER_PATH.containsMatchIn(line)) "${generated.relativize(path)}:${index + 1}: ${line.trim()}" else null
                    }
                }
            offenders.shouldBeEmpty()
        }
    }

    Given("MapstructConfiguration 과 mapper 패키지의 @Mapper 인터페이스 (ClassGraph — CLASS retention 애노테이션)") {
        Then("M-3 unmappedTargetPolicy 가 ERROR 다") {
            scan().use { scan ->
                val config = scan.getClassInfo("$BASE.config.MapstructConfiguration").shouldNotBeNull()
                val mapperConfig = config.getAnnotationInfo(MAPPER_CONFIG).shouldNotBeNull()
                mapperConfig.parameterValues.getValue("unmappedTargetPolicy").toString() shouldEndWith "ERROR"
            }
        }

        Then("M-4 ignore = true 인 타깃은 nullable 이거나 @Id 다 — JPA Entity 도 예외 없음") {
            scan().use { scan ->
                var checked = 0
                val violations =
                    mapperFunctions(scan).flatMap { (methodInfo, function) ->
                        val target = function.returnClass() ?: return@flatMap emptyList()
                        mappingsOf(methodInfo).filter { it.ignore }.mapNotNull { mapping ->
                            checked++
                            val property = target.memberProperties.firstOrNull { it.name == mapping.target }
                            when {
                                property == null -> "${methodInfo.classInfo.simpleName}.${function.name}: ignore 타깃 '${mapping.target}' 이 ${target.simpleName} 에 없다"
                                property.returnType.isMarkedNullable || property.isId(target) -> null
                                else -> "${methodInfo.classInfo.simpleName}.${function.name}: non-null 타깃 '${mapping.target}' 을 ignore — null 이 들어간다"
                            }
                        }
                    }
                println("checked $checked ignore mappings")
                violations.shouldBeEmpty()
            }
        }

        Then("M-5 nullable 소스 → non-null 타깃은 null 경로를 닫은 매핑만 허용한다 — data class·Entity 모두") {
            scan().use { scan ->
                var checkedTargets = 0
                var checkedPairs = 0
                val violations =
                    mapperFunctions(scan).flatMap { (methodInfo, function) ->
                        val target = function.returnClass() ?: return@flatMap emptyList()
                        val nonNullTargets = target.nonNullTargetNames() ?: return@flatMap emptyList()
                        checkedTargets += nonNullTargets.size
                        val mappings = mappingsOf(methodInfo)
                        val sourceParams = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
                        nonNullTargets.mapNotNull { name ->
                            val mapping = mappings.firstOrNull { it.target == name }
                            if (mapping?.ignore == true) return@mapNotNull null // M-4 가 본다
                            // 소스가 nullable 인가 — 명시 source 경로가 있으면 그 경로, 없으면 같은 이름의 자동 매핑
                            val nullableSource =
                                if (mapping != null && mapping.source.isNotBlank()) {
                                    sourceNullable(mapping.source, sourceParams)
                                } else {
                                    sourceParams.any { p ->
                                        (p.type.classifier as? KClass<*>)?.memberProperties?.firstOrNull { it.name == name }?.returnType?.isMarkedNullable ==
                                            true
                                    }
                                }
                            val namedNullable =
                                mapping != null && mapping.qualifiedByName.isNotEmpty() && !namedReturnsNonNull(methodInfo.classInfo, mapping.qualifiedByName.first())
                            if (!nullableSource && !namedNullable) return@mapNotNull null
                            checkedPairs++
                            val closed =
                                mapping != null &&
                                    (
                                        mapping.expression.isNotBlank() || mapping.defaultValue.isNotBlank() || mapping.defaultExpression.isNotBlank() ||
                                            (mapping.qualifiedByName.isNotEmpty() && namedReturnsNonNull(methodInfo.classInfo, mapping.qualifiedByName.first()))
                                        )
                            if (closed) {
                                null
                            } else {
                                "${methodInfo.classInfo.simpleName}.${function.name}: nullable 소스 → non-null 타깃 '$name' 의 null 경로가 열려 있다" +
                                    "(expression/defaultValue/defaultExpression 또는 non-null @Named 로 닫는다)"
                            }
                        }
                    }
                println("checked $checkedTargets non-null targets, $checkedPairs nullable-source pairs")
                violations.forEach(::println)
                checkedTargets shouldBeGreaterThan 0
                violations.shouldBeEmpty()
            }
        }
    }
}) {
    companion object {
        private const val BASE = "com.rex.api.pub.skeleton"
        private const val MAPPER = "org.mapstruct.Mapper"
        private const val MAPPER_CONFIG = "org.mapstruct.MapperConfig"
        private const val MAPPING = "org.mapstruct.Mapping"
        private const val MAPPINGS = "org.mapstruct.Mappings"
        private const val NAMED = "org.mapstruct.Named"
        private val ID_ANNOTATIONS = setOf("jakarta.persistence.Id", "org.springframework.data.annotation.Id")

        /** setter 경로의 유일한 서명 — 생성자 경로는 항상 인자를 갖는다. */
        val SETTER_PATH = Regex("""new \w+(Dto|Model)\(\);""")

        private fun scan(): ScanResult = ClassGraph().acceptPackages("$BASE.mapper", "$BASE.config").enableAllInfo().scan()

        /** @Mapper 인터페이스의 매핑 함수 — @Named 변환 헬퍼·정적 멤버는 제외. */
        private fun mapperFunctions(scan: ScanResult): List<Pair<MethodInfo, KFunction<*>>> =
            scan.getClassesWithAnnotation(MAPPER).flatMap { classInfo ->
                classInfo.methodInfo
                    .filter { !it.hasAnnotation(NAMED) && !it.isStatic && !it.isSynthetic }
                    .mapNotNull { methodInfo -> methodInfo.loadClassAndGetMethod().kotlinFunction?.let { methodInfo to it } }
            }

        private fun KFunction<*>.returnClass(): KClass<*>? = returnType.classifier as? KClass<*>

        /** 검사할 non-null 타깃 — data class 는 주 생성자 파라미터, @Entity 는 setter(var) 프로퍼티. 그 외 타입(컬렉션 등)은 대상 아님. */
        private fun KClass<*>.nonNullTargetNames(): List<String>? =
            when {
                isData -> primaryConstructor?.parameters?.filter { !it.type.isMarkedNullable }?.mapNotNull { it.name }
                java.isAnnotationPresent(Entity::class.java) -> memberProperties.filter { it is KMutableProperty1 && !it.returnType.isMarkedNullable }.map { it.name }
                else -> null
            }

        /** Kotlin 은 use-site 를 안 적으면 Java 애노테이션(@Target FIELD/METHOD)을 field 에 붙인다 — 프로퍼티·필드·getter·생성자 파라미터 네 곳을 본다. */
        private fun KProperty1<*, *>.isId(owner: KClass<*>): Boolean {
            val annotations =
                annotations +
                    (javaField?.annotations?.toList() ?: emptyList()) +
                    (getter.javaMethod?.annotations?.toList() ?: emptyList()) +
                    (owner.primaryConstructor?.parameters?.firstOrNull { it.name == name }?.annotations ?: emptyList())
            return annotations.any { it.annotationClass.qualifiedName in ID_ANNOTATIONS }
        }

        /**
         * `@Mapping(source = "…")` 경로의 nullability — `param.prop.sub` 또는 `prop`(첫 소스 파라미터부터 탐색).
         * 경로 어디든 nullable 이면 true. 해석 불가(존재하지 않는 경로)는 MapStruct 가 컴파일 단계에서 막으므로 false 로 둔다.
         */
        private fun sourceNullable(path: String, sourceParams: List<KParameter>): Boolean {
            val segments = path.split('.')
            val byParam = sourceParams.firstOrNull { it.name == segments.first() }
            var type: KType = byParam?.type ?: run {
                val owner = sourceParams.firstOrNull { p -> (p.type.classifier as? KClass<*>)?.memberProperties?.any { it.name == segments.first() } == true } ?: return false
                owner.type
            }
            val rest = if (byParam != null) segments.drop(1) else segments
            if (type.isMarkedNullable) return true
            for (segment in rest) {
                val property = (type.classifier as? KClass<*>)?.memberProperties?.firstOrNull { it.name == segment } ?: return false
                type = property.returnType
                if (type.isMarkedNullable) return true
            }
            return false
        }

        private fun namedReturnsNonNull(mapper: ClassInfo, name: String): Boolean =
            mapper.methodInfo
                .firstOrNull { it.getAnnotationInfo(NAMED)?.parameterValues?.getValue("value") == name }
                ?.loadClassAndGetMethod()?.kotlinFunction?.returnType?.isMarkedNullable == false

        private data class MappingSpec(
            val target: String,
            val source: String,
            val ignore: Boolean,
            val expression: String,
            val defaultValue: String,
            val defaultExpression: String,
            val qualifiedByName: List<String>,
        )

        /**
         * 반복 @Mapping 은 @Mappings 컨테이너로 컴파일되지만 ClassGraph 는 스캔 조건에 따라 컨테이너를 풀어 개별 @Mapping 으로 노출한다 —
         * 둘 다 받는다(개별 @Mapping 전부 + 남아 있는 컨테이너의 value 전개).
         */
        private fun mappingsOf(methodInfo: MethodInfo): List<MappingSpec> {
            val single = methodInfo.annotationInfo.filter { it.name == MAPPING }
            val container =
                methodInfo.annotationInfo.filter { it.name == MAPPINGS }
                    .flatMap { (it.parameterValues.getValue("value") as? Array<*>)?.filterIsInstance<AnnotationInfo>() ?: emptyList() }
            return (single + container).map { info ->
                val values = info.parameterValues
                fun text(key: String): String = values.getValue(key) as? String ?: ""
                MappingSpec(
                    target = text("target"),
                    source = text("source"),
                    ignore = values.getValue("ignore") as? Boolean ?: false,
                    expression = text("expression"),
                    defaultValue = text("defaultValue"),
                    defaultExpression = text("defaultExpression"),
                    qualifiedByName = (values.getValue("qualifiedByName") as? Array<*>)?.map { it.toString() } ?: emptyList(),
                )
            }
        }
    }
}