package com.rex.api.pub.skeleton.convention

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension

/**
 * `@Async` 자기 호출 금지 규약 검사 (`.claude/rules/conventions.md` 「데이터베이스」 3).
 *
 * `@Async` 도 `@Transactional` 과 같은 프록시 규칙이다 — 같은 클래스 안에서 부르면 프록시를 지나지 않아 **동기로 돈다**.
 * 리플렉션은 호출 그래프를 보지 못하므로 `src/main/kotlin` 소스를 읽어 `@Async fun X` 와 같은 파일 안의
 * 무한정 `X(` 또는 `this.X(` 호출을 대조한다. 비동기가 필요하면 `@Async` 메서드를 별도 빈으로 나눈다(`ExampleAsyncProxyWorker`).
 */
class AsyncSelfInvocationConventionTest : BehaviorSpec({

    Given("src/main/kotlin 의 @Async 메서드") {
        val files = Files.walk(Paths.get("src/main/kotlin")).use { stream -> stream.filter { it.extension == "kt" }.toList() }
        val hits = files.flatMap { file -> asyncMethods(Files.readString(file)).map { file to it } }
        println("checked ${hits.size} @Async methods in ${hits.map { it.first }.distinct().size} files")

        Then("A-1 @Async 메서드를 같은 파일 안에서 자기 호출하지 않는다 (무한정 X( 또는 this.X()") {
            hits.shouldNotBeEmpty()
            val violations = hits.flatMap { (file, method) -> selfCalls(Files.readString(file), method).map { line -> "$file:$line ${method.name}(" } }
            violations.forEach { println("A-1 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("A-2 @Async 메서드는 private·protected 가 아니다 (프록시가 가로채지 못한다)") {
            hits.filter { (_, method) -> method.isHidden() }.map { "${it.first} ${it.second.name}" }.shouldBeEmpty()
        }
    }

    Given("검출 정규식 자체") {
        Then("픽스처 — 무한정 호출·this. 호출은 위반, 다른 수신자의 호출은 아니다") {
            val src =
                "class A {\n    @Async\n    fun process(id: String) {}\n    fun a() { process(\"x\") }\n" +
                    "    fun b() { this.process(\"y\") }\n    fun c(o: A) { o.process(\"z\") }\n}\n"
            selfCalls(src, asyncMethods(src).single()) shouldBe listOf(4, 5)
        }

        Then("픽스처 — @Async private·protected fun 은 A-2 위반으로 잡힌다") {
            val src = "class B {\n    @Async\n    private fun hidden() {}\n    @Async\n    protected open fun half() {}\n    @Async\n    fun ok() {}\n}\n"
            asyncMethods(src).filter { it.isHidden() }.map { it.name } shouldBe listOf("hidden", "half")
        }
    }
}) {
    companion object {
        data class AsyncMethod(val name: String, val line: Int, val modifiers: List<String>) {
            fun isHidden(): Boolean = modifiers.any { it == "private" || it == "protected" }
        }

        /** `@Async` 줄 → (다른 애노테이션 줄)* → 수식어 → `fun 이름(`. 그룹 1 = 수식어, 그룹 2 = 이름. */
        private val DECLARATION =
            Regex("""@Async[^\n]*\n(?:[ \t]*@[^\n]*\n)*[ \t]*((?:(?:private|protected|public|internal|override|open|suspend)\s+)*)fun\s+(\w+)\s*\(""")

        fun asyncMethods(src: String): List<AsyncMethod> =
            DECLARATION.findAll(src).map { m ->
                AsyncMethod(
                    name = m.groupValues[2],
                    line = src.lineAt(m.range.last),
                    modifiers = m.groupValues[1].trim().split(Regex("""\s+""")).filter { it.isNotBlank() },
                )
            }.toList()

        /** 수신자 없는 `X(` 또는 `this.X(` — 선언 줄 자체는 제외. `other.X(` 는 다른 빈 호출이라 위반이 아니다. */
        fun selfCalls(src: String, method: AsyncMethod): List<Int> =
            Regex("""(?:(?<![\w.])|(?<=\bthis\.))${Regex.escape(method.name)}\s*\(""")
                .findAll(src)
                .map { src.lineAt(it.range.first) }
                .filter { it != method.line }
                .toList()

        private fun String.lineAt(offset: Int): Int = substring(0, offset).count { it == '\n' } + 1
    }
}