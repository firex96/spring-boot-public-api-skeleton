package com.rex.api.pub.skeleton.convention

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension

/**
 * 「1 Class per 1 File」 규약 검사 (README 「코딩 규약」).
 *
 * 최상위 `class`·`interface`·`object`(enum·data·annotation 포함)는 파일에 하나만 둔다. 예외는 Kotlin extension 파일(최상위 함수·프로퍼티만)과
 * `sealed` 계층(sealed 타입과 그 직접 하위 타입)뿐이며 `typealias`·`fun main` 은 세지 않는다. 리플렉션은 파일 경계를 보지 못하므로
 * `src/main/kotlin` 과 테스트 소스셋(`src/test/<소스셋>/kotlin`) 소스를 읽어 컬럼 0 선언을 센다(블록 주석·raw string·컬럼 0 줄 주석은 같은 길이 공백으로 지워 줄 번호를 보존).
 * 파일 이름 = 클래스 이름은 ktlint `standard:filename` 이 강제하므로 여기서는 보지 않는다.
 * 규칙마다 레포별 최소 스캔 대상 수(`MIN`·`MIN_TEST`)를 별도 케이스(OC-0·OC-5)로 단언해 스캐너·경로 고장이 공허 통과하지 않는다 — 세 스켈레톤이 이 파일을 글자 그대로 공유한다.
 */
class OneClassPerFileConventionTest : BehaviorSpec({

    Given("src/main/kotlin 의 .kt 파일") {
        val declarationsByFile = scan(listOf("src/main/kotlin"))
        val declarationCount = declarationsByFile.values.sumOf { it.size }
        println("OC-0 files=${declarationsByFile.size} declarations=$declarationCount (min ${MIN.files}/${MIN.declarations})")

        Then("OC-0 스캔 파일·선언 수가 최소 수 이상이다 (스캐너·경로 고장으로 공허 통과하지 않는다)") {
            declarationsByFile.size shouldBeGreaterThanOrEqual MIN.files
            declarationCount shouldBeGreaterThanOrEqual MIN.declarations
        }

        Then("OC-1 파일마다 세는 최상위 선언은 하나다 (extension 파일·sealed 계층 예외)") {
            val violations = violations(declarationsByFile)
            violations.forEach { println("OC-1 violation: $it") }
            violations.shouldBeEmpty()
        }
    }

    Given("테스트 소스셋의 .kt 파일") {
        val declarationsByFile = scan(TEST_ROOTS)
        val declarationCount = declarationsByFile.values.sumOf { it.size }
        println("OC-5 files=${declarationsByFile.size} declarations=$declarationCount (min ${MIN_TEST.files}/${MIN_TEST.declarations})")

        Then("OC-5 스캔 파일·선언 수가 최소 수 이상이다 (스캐너·경로 고장으로 공허 통과하지 않는다)") {
            declarationsByFile.size shouldBeGreaterThanOrEqual MIN_TEST.files
            declarationCount shouldBeGreaterThanOrEqual MIN_TEST.declarations
        }

        Then("OC-6 테스트 코드도 파일마다 세는 최상위 선언은 하나다 (테스트용 컨트롤러·요청 클래스도 각자 파일)") {
            val violations = violations(declarationsByFile)
            violations.forEach { println("OC-6 violation: $it") }
            violations.shouldBeEmpty()
        }
    }

    Given("검출 규칙 자체 (픽스처)") {
        Then("OC-2 data class 두 개는 위반이다") {
            val src = "package a\n\ndata class A(val x: Int)\n\ndata class B(val y: Int)\n"
            countableDeclarations(topLevelDeclarations(src)).map { it.name } shouldContainExactly listOf("A", "B")
        }

        Then("OC-3 sealed 계층은 예외지만 무관한 클래스는 센다") {
            val hierarchy = "sealed class R\n\ndata class A(val x: Int) : R()\n\nobject B : R()\n\nclass C\n"
            countableDeclarations(topLevelDeclarations(hierarchy)).map { it.name } shouldContainExactly listOf("R", "C")
            val pure = "sealed class R\n\ndata class A(val x: Int) : R()\n"
            countableDeclarations(topLevelDeclarations(pure)).map { it.name } shouldContainExactly listOf("R")
            val interfaceHierarchy = "sealed interface S\n\nclass Impl(val v: Int) : Base(), S\n\nobject Empty : S\n"
            countableDeclarations(topLevelDeclarations(interfaceHierarchy)).map { it.name } shouldContainExactly listOf("S")
        }

        Then("OC-4 주석·문자열·typealias·최상위 함수·중첩 선언은 세지 않고, fun interface·private class·annotation·enum 은 센다") {
            val notCounted =
                "package a\n\n/**\n * class InDoc\n */\nval raw = \"\"\"\nclass InRaw\n\"\"\"\n// class InLineComment\ntypealias T = A\n\nfun main() {\n    class Local\n}\n\nval x = 1\n"
            topLevelDeclarations(notCounted).shouldBeEmpty()
            val counted = "fun interface F {\n    fun f()\n}\n\nprivate class P\n\nannotation class An\n\nenum class E { A }\n"
            topLevelDeclarations(counted).map { it.name } shouldContainExactly listOf("F", "P", "An", "E")
            topLevelDeclarations("data class Only(val a: Int)\n").size shouldBe 1
        }
    }
}) {
    companion object {
        /** 테스트 클래스의 패키지에서 base package 를 얻는다 — 세 스켈레톤이 이 파일을 글자 그대로 공유한다. */
        private val BASE = OneClassPerFileConventionTest::class.java.packageName.removeSuffix(".convention")

        /** 레포 식별자 — base package 의 `.skeleton` 앞 세그먼트(`pub`·`internal`·`batch`). base package 문자열은 여기 두지 않는다. */
        private val REPO = BASE.removeSuffix(".skeleton").substringAfterLast('.')

        /** 레포별 최소 스캔 대상 수 — 이보다 적으면 규약 위반이 아니라 스캐너·workingDir 가 잘못된 것이다(공허 통과 방지). */
        private data class MinTargets(val files: Int, val declarations: Int)

        private val MIN =
            mapOf(
                "pub" to MinTargets(files = 250, declarations = 250),
                "internal" to MinTargets(files = 100, declarations = 100),
                "batch" to MinTargets(files = 140, declarations = 140),
            ).getValue(REPO)

        /** 테스트 소스셋의 최소 스캔 대상 수 — `MIN` 과 같은 목적이다. 2026-10-26 실측(public 169·163, internal 67·67, batch 58·58)의 약 90%. */
        private val MIN_TEST =
            mapOf(
                "pub" to MinTargets(files = 150, declarations = 140),
                "internal" to MinTargets(files = 60, declarations = 60),
                "batch" to MinTargets(files = 50, declarations = 50),
            ).getValue(REPO)

        /** 스캔하는 테스트 소스셋 — `src/test/<소스셋>/kotlin` 을 찾아 쓴다. 레포마다 소스셋이 다르고(batch 에는 e2e 가 없다) 새 소스셋도 빠지지 않게 한다. */
        private val TEST_ROOTS: List<String> =
            Paths.get("src/test").takeIf { Files.isDirectory(it) }?.let { root ->
                Files.list(root).use { dirs -> dirs.map { it.resolve("kotlin") }.filter { Files.isDirectory(it) }.map { it.toString() }.toList() }
            } ?: emptyList()

        private fun scan(roots: List<String>): Map<Path, List<Declaration>> =
            roots
                .map { Paths.get(it) }
                .filter { Files.isDirectory(it) }
                .flatMap { root -> Files.walk(root).use { stream -> stream.filter { it.extension == "kt" }.toList() } }
                .associateWith { topLevelDeclarations(Files.readString(it)) }

        private fun violations(declarationsByFile: Map<Path, List<Declaration>>): List<String> =
            declarationsByFile.mapNotNull { (file, decls) ->
                val countable = countableDeclarations(decls)
                if (countable.size > 1) "$file: ${countable.joinToString(", ") { it.name }} (${countable.size} declarations)" else null
            }.sorted()

        /** 블록 주석 · raw string · 컬럼 0 줄 주석 — 같은 길이 공백으로 지워 줄 번호를 보존한다. */
        private val NOISE = Regex("""/\*[\s\S]*?\*/|"{3}[\s\S]*?"{3}|^//[^\n]*""", RegexOption.MULTILINE)

        /** 컬럼 0 의 class·interface·object 선언(수식어 선행 허용). typealias·최상위 fun/val·들여쓴 선언은 잡지 않는다. */
        private val DECLARATION =
            Regex(
                """^(?:(?:public|private|internal|protected|abstract|open|final|sealed|data|enum|annotation|value|inline|inner|fun|expect|actual)\s+)*(?:class|interface|object)\s+(\w+)""",
                RegexOption.MULTILINE,
            )

        /** 최상위 선언 — `header` 는 선언 시작부터 첫 `{`(또는 다음 선언 직전)까지로, 상위 타입 목록이 들어 있다. */
        data class Declaration(val name: String, val sealed: Boolean, val header: String)

        private fun blankNoise(src: String): String = NOISE.replace(src) { m -> m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }

        fun topLevelDeclarations(src: String): List<Declaration> {
            val cleaned = blankNoise(src)
            val matches = DECLARATION.findAll(cleaned).toList()
            return matches.mapIndexed { i, m ->
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else cleaned.length
                val segment = cleaned.substring(m.range.first, end)
                Declaration(name = m.groupValues[1], sealed = Regex("""\bsealed\s""").containsMatchIn(m.value), header = segment.substringBefore("{"))
            }
        }

        /** sealed 예외 — 파일에 최상위 sealed 가 정확히 하나(S)면 S 를 상위 타입으로 갖는 선언은 세지 않는다. */
        fun countableDeclarations(decls: List<Declaration>): List<Declaration> {
            val sealedNames = decls.filter { it.sealed }.map { it.name }
            if (sealedNames.size != 1) return decls
            val s = sealedNames.single()
            val extendsSealed = Regex("""[:,]\s*$s\b""")
            return decls.filterNot { !it.sealed && extendsSealed.containsMatchIn(it.header) }
        }
    }
}