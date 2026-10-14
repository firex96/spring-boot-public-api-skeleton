package com.rex.api.pub.skeleton.convention

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.model.audit.AuditLogModel
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientModel
import com.rex.api.pub.skeleton.model.security.BlockedIpModel
import com.rex.api.pub.skeleton.model.security.LoginAttemptStatusModel
import com.rex.api.pub.skeleton.model.security.SecuritySettingsModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

/**
 * 데모 관리자 페이지(`templates/demo/admin` 의 HTML 7)가 실제 API 계약을 따르는지 — 응답 필드는 Model 의 `@JsonProperty` 이름, 경로는
 * `controller/admin` 의 매핑, 요청 본문은 핸들러 `@RequestBody` DTO 의 생성자 파라미터. 템플릿은 어떤 테스트도 지나가지 않아
 * 존재하지 않는 필드(`client.name`)·라우트(`/activate`)·본문 키(`{permissionId}`)를 오래 품고 있었다.
 * Spring Context 없이 소스(HTML 인라인 스크립트)와 리플렉션만 쓴다. 요청 DTO 는 핸들러에서 얻으므로 DTO 파일 분리·개명과 무관하다.
 *
 * 한계: 경로 변수 `{}` 는 타입을 모른다(`/blocked-ips/{ipAddress}` 와 `/{id}` 를 구별하지 못함 — 브라우저 e2e 가 보완) ·
 * 변수로 조립한 URL(`let url = …`)은 보지 못한다(템플릿은 리터럴 경로만 쓴다).
 */
class DemoTemplateContractTest : BehaviorSpec({

    Given("templates/demo/admin 의 인라인 스크립트와 controller.admin 의 라우트") {
        val templates = Files.list(TEMPLATE_DIR).use { s -> s.filter { it.extension == "html" }.sorted().toList() }
        val pages = templates.map { Page(it.name.removeSuffix(".html"), Files.readString(it)) }
        val handlers = adminHandlers()
        val routes = handlers.map { it.route }.toSet()
        val bindingCount = pages.sumOf { BINDINGS[it.name]?.size ?: 0 }
        println("checked templates=${pages.size} routes=${routes.size} bindings=$bindingCount")

        Then("C-0 템플릿 7 · 라우트 40 이상 (공허 통과 방지)") {
            pages.size shouldBe 7
            routes.size shouldBeGreaterThanOrEqual 40
        }

        Then("C-1 행 변수의 프로퍼티는 응답 Model 의 @JsonProperty 이름이다") {
            val violations =
                pages.flatMap { page ->
                    val bindings = BINDINGS[page.name] ?: emptyMap()
                    bindings.flatMap { (variable, model) ->
                        val allowed = jsonProperties(model)
                        propertyAccesses(page.script, variable)
                            .filterNot { (prop, _) -> prop in allowed || prop in JS_BUILTINS }
                            .map { (prop, line) -> "${page.name}.html:$line $variable.$prop — ${model.simpleName} 에 없음(후보: ${candidates(prop, allowed)})" }
                    }
                }
            violations.forEach { println("C-1 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("C-2 스크립트의 /admin 경로는 컨트롤러 매핑에 있다 (apiCall 은 메서드까지)") {
            val paths = routes.map { it.path }.toSet()
            val violations =
                pages.flatMap { page ->
                    val byPath =
                        adminPathLiterals(page.script).filterNot { (path, _) -> normalizePath(path) in paths }
                            .map { (path, line) -> "${page.name}.html:$line ${normalizePath(path)} — 라우트 없음" }
                    val byCall =
                        apiCalls(page.script).filterNot { call -> Route(call.method, normalizePath(call.path)) in routes }
                            .map { call -> "${page.name}.html:${call.line} ${call.method} ${normalizePath(call.path)} — 라우트 없음" }
                    byPath + byCall
                }
            violations.forEach { println("C-2 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("C-3 정보 상자의 <code>METHOD /admin/…</code> 도 실제 라우트다") {
            val violations =
                pages.flatMap { page ->
                    infoBoxRoutes(page.html).filterNot { (route, _) -> route in routes }
                        .map { (route, line) -> "${page.name}.html:$line ${route.method} ${route.path} — 라우트 없음" }
                }
            violations.forEach { println("C-3 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("C-5 innerHTML 에 들어가는 서버 값은 escapeHtml 을 지난다 (stored XSS 방지)") {
            val violations =
                pages.flatMap { page ->
                    val bindings = BINDINGS[page.name] ?: emptyMap()
                    unescapedInterpolations(page.script, bindings.keys + setOf("response", "error"))
                        .map { (expr, line) -> "${page.name}.html:$line \${$expr} — escapeHtml 없이 innerHTML 에 들어간다" }
                }
            violations.forEach { println("C-5 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("C-6 인라인 이벤트 핸들러 속성에는 보간을 넣지 않는다 (JS 문자열 문맥 이스케이프 부재)") {
            val violations =
                pages.flatMap { page ->
                    inlineHandlerInterpolations(page.html)
                        .map { (attr, line) -> "${page.name}.html:$line $attr — 인라인 핸들러에 값을 끼워 넣었다. data-* + addEventListener 로 바꿔라" }
                }
            violations.forEach { println("C-6 violation: $it") }
            violations.shouldBeEmpty()
        }

        Then("C-4 apiCall 본문의 키는 @RequestBody DTO 의 생성자 파라미터이고 필수 파라미터를 다 보낸다") {
            val violations =
                pages.flatMap { page ->
                    apiCalls(page.script).filter { it.bodyKeys != null }.flatMap { call ->
                        val handler = handlers.firstOrNull { it.route == Route(call.method, normalizePath(call.path)) } ?: return@flatMap emptyList()
                        val params = handler.bodyParameters ?: return@flatMap emptyList()
                        val sent = call.bodyKeys!!
                        val unknown = sent - params.map { it.name }.toSet()
                        val missing = params.filter { it.required }.map { it.name }.toSet() - sent
                        listOfNotNull(
                            unknown.takeIf { it.isNotEmpty() }?.let { "${page.name}.html:${call.line} ${call.method} ${call.path} 미존재 키 $it" },
                            missing.takeIf { it.isNotEmpty() }?.let { "${page.name}.html:${call.line} ${call.method} ${call.path} 필수 누락 $it" },
                        )
                    }
                }
            violations.forEach { println("C-4 violation: $it") }
            violations.shouldBeEmpty()
        }
    }

    Given("검출 정규식 자체 (픽스처)") {
        Then("프로퍼티 접근 · 경로 정규화 · 본문 키 · 정보 상자 추출") {
            propertyAccesses("`\${client.name}` + client.id", "client").map { it.first } shouldContainExactly listOf("name", "id")
            normalizePath("/admin/x/\${id}/activate?x=1") shouldBe "/admin/x/{}/activate"
            val call = apiCalls("apiCall('/admin/x', 'POST', { name: a.value, redirectUris, nested: { z: 1 }, list: [1, 2] })").single()
            call.method shouldBe "POST"
            call.bodyKeys shouldContainExactlyInAnyOrder listOf("name", "redirectUris", "nested", "list")
            apiCalls("await apiCall(`/admin/y/\${id}`)").single().method shouldBe "GET"
            infoBoxRoutes("<code>GET /admin/x/{id}</code>").map { it.first } shouldContainExactly listOf(Route("GET", "/admin/x/{}"))
            val html = "x.innerHTML = `<td>\${escapeHtml(client.clientName)}</td><td>\${client.name}</td><td>\${client.id}</td>`;"
            // escapeHtml 을 지난 값과 숫자 식별자(`.id`)는 보고하지 않는다 — 나머지 문자열 값만 위반이다
            unescapedInterpolations(html, setOf("client")).map { it.first } shouldContainExactly listOf("client.name")
            // escapeHtml 을 지나도 인라인 핸들러 안이면 위반이다 — HTML 파서가 엔티티를 먼저 되돌린 뒤 JS 로 컴파일한다
            inlineHandlerInterpolations("<button onclick=\"unblockIp('\${escapeHtml(ip.ipAddress)}')\">x</button>").map { it.first } shouldContainExactly
                listOf("onclick=\"unblockIp('\${escapeHtml(ip.ipAddress)}')\"")
            inlineHandlerInterpolations("<button type=\"button\" data-ip=\"\${escapeHtml(ip.ipAddress)}\" onclick=\"unblock()\">x</button>").shouldBeEmpty()
            // 작은따옴표 속성도 같은 결함이다 — 따옴표 종류로 검출을 피할 수 없어야 한다
            inlineHandlerInterpolations("<button onclick='unblockIp(\"\${escapeHtml(ip.ipAddress)}\")'>x</button>").map { it.first } shouldContainExactly
                listOf("onclick='unblockIp(\"\${escapeHtml(ip.ipAddress)}\")'")
        }
    }
}) {
    companion object {
        private val BASE = DemoTemplateContractTest::class.java.packageName.removeSuffix(".convention")
        private val TEMPLATE_DIR: Path = Paths.get("src/main/resources/templates/demo/admin")

        /** 페이지 → 행 변수 → 응답 Model. 페이지가 반복하는 항목의 변수 이름만 등록한다. */
        private val BINDINGS: Map<String, Map<String, KClass<*>>> =
            mapOf(
                "index" to mapOf("log" to AuditLogModel::class),
                "oauth2-clients" to mapOf("client" to OAuth2ClientModel::class),
                "groups" to mapOf("group" to GroupModel::class, "perm" to PermissionModel::class, "p" to PermissionModel::class),
                "permissions" to mapOf("perm" to PermissionModel::class),
                "users" to mapOf("group" to GroupModel::class, "g" to GroupModel::class, "perm" to PermissionModel::class, "p" to PermissionModel::class),
                "security" to mapOf("ip" to BlockedIpModel::class, "status" to LoginAttemptStatusModel::class, "settings" to SecuritySettingsModel::class),
                "audit-logs" to mapOf("log" to AuditLogModel::class),
            )

        private val JS_BUILTINS =
            setOf(
                "length", "map", "forEach", "join", "filter", "includes", "some", "find", "findIndex", "toString", "toLowerCase", "toUpperCase",
                "replace", "split", "trim", "slice", "indexOf", "push", "toLocaleString", "toISOString",
            )

        private val SCRIPT = Regex("""<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)</script>""")
        private val ADMIN_PATH = Regex("""(['"`])(/admin/[^'"`\s]*)\1""")
        private val INFO_BOX = Regex("""<code>(GET|POST|PUT|DELETE|PATCH)\s+(/admin/[^<\s]*)</code>""")
        private val BODY_KEY = Regex("""(?:^|[{,])\s*([A-Za-z_$][\w$]*)\s*(?=[:,}])""")

        data class Page(val name: String, val html: String) {
            val script: String = SCRIPT.findAll(html).joinToString("\n") { it.groupValues[1] }
        }

        data class Route(val method: String, val path: String)

        data class BodyParameter(val name: String, val required: Boolean)

        data class Handler(val route: Route, val bodyParameters: List<BodyParameter>?)

        data class ApiCall(val method: String, val path: String, val bodyKeys: Set<String>?, val line: Int)

        // ── 컨트롤러 라우트 · @RequestBody 파라미터

        /** `value`·`path` 는 non-null Array alias — 비어 있지 않은 쪽을 고르고 둘 다 비면 `[""]`(`@GetMapping` 처럼 경로 없는 매핑). */
        private fun paths(value: Array<String>, path: Array<String>): List<String> = (value.takeIf { it.isNotEmpty() } ?: path).ifEmpty { arrayOf("") }.toList()

        private fun methodMappings(fn: KFunction<*>): List<Pair<String, List<String>>> =
            listOfNotNull(
                fn.findAnnotation<GetMapping>()?.let { "GET" to paths(it.value, it.path) },
                fn.findAnnotation<PostMapping>()?.let { "POST" to paths(it.value, it.path) },
                fn.findAnnotation<PutMapping>()?.let { "PUT" to paths(it.value, it.path) },
                fn.findAnnotation<DeleteMapping>()?.let { "DELETE" to paths(it.value, it.path) },
                fn.findAnnotation<PatchMapping>()?.let { "PATCH" to paths(it.value, it.path) },
                fn.findAnnotation<RequestMapping>()?.let { rm -> rm.method.firstOrNull()?.name?.let { it to paths(rm.value, rm.path) } },
            )

        private fun adminHandlers(): List<Handler> {
            val provider = ClassPathScanningCandidateComponentProvider(false).apply { addIncludeFilter(AnnotationTypeFilter(RestController::class.java)) }
            return provider.findCandidateComponents("$BASE.controller.admin")
                .map { Class.forName(it.beanClassName).kotlin }
                .flatMap { controller ->
                    val prefixes = controller.findAnnotation<RequestMapping>()?.let { paths(it.value, it.path) } ?: listOf("")
                    controller.declaredMemberFunctions.flatMap { fn ->
                        val body =
                            fn.parameters.firstOrNull { it.hasAnnotation<RequestBody>() }?.let { p ->
                                (p.type.classifier as KClass<*>).primaryConstructor!!.parameters.map { c ->
                                    BodyParameter(c.name!!, required = !c.isOptional && !c.type.isMarkedNullable)
                                }
                            }
                        methodMappings(fn).flatMap { (method, mapped) ->
                            prefixes.flatMap { prefix -> mapped.map { m -> Handler(Route(method, normalizePath("$prefix/$m")), body) } }
                        }
                    }
                }
        }

        // ── 템플릿 추출

        fun normalizePath(raw: String): String =
            raw.substringBefore('?').replace(Regex("""\$\{[^}]*\}"""), "{}").replace(Regex("""\{[^}]+\}"""), "{}")
                .replace(Regex("/{2,}"), "/").trimEnd('/').ifEmpty { "/" }

        private fun lineOf(text: String, index: Int): Int = text.substring(0, index).count { it == '\n' } + 1

        fun propertyAccesses(script: String, variable: String): List<Pair<String, Int>> =
            Regex("""\b${Regex.escape(variable)}\.([A-Za-z_$][\w$]*)""").findAll(script).map { it.groupValues[1] to lineOf(script, it.range.first) }.toList()

        fun adminPathLiterals(script: String): List<Pair<String, Int>> = ADMIN_PATH.findAll(script).map { it.groupValues[2] to lineOf(script, it.range.first) }.toList()

        fun infoBoxRoutes(html: String): List<Pair<Route, Int>> =
            INFO_BOX.findAll(html).map { Route(it.groupValues[1], normalizePath(it.groupValues[2])) to lineOf(html, it.range.first) }.toList()

        /** `apiCall(` 호출마다 괄호 균형으로 인자를 나눈다 — 경로 리터럴, 메서드(기본 GET), 본문 객체의 깊이-1 키. */
        fun apiCalls(script: String): List<ApiCall> {
            val calls = mutableListOf<ApiCall>()
            var from = 0
            while (true) {
                val start = script.indexOf("apiCall(", from)
                if (start < 0) break
                val open = start + "apiCall".length
                val close = matchingParen(script, open) ?: break
                val args = splitTopLevel(script.substring(open + 1, close))
                from = close + 1
                val path = args.getOrNull(0)?.trim()?.let { ADMIN_PATH.matchEntire(it)?.groupValues?.get(2) } ?: continue
                val method = args.getOrNull(1)?.trim()?.trim('\'', '"', '`')?.takeIf { it.isNotEmpty() } ?: "GET"
                val body = args.getOrNull(2)?.trim()
                val keys = body?.takeIf { it.startsWith("{") }?.let { BODY_KEY.findAll(maskNested(it)).map { m -> m.groupValues[1] }.toSet() }
                calls += ApiCall(method, path, keys, lineOf(script, start))
            }
            return calls
        }

        private fun matchingParen(text: String, open: Int): Int? {
            var depth = 0
            var quote: Char? = null
            var i = open
            while (i < text.length) {
                val c = text[i]
                when {
                    quote != null -> if (c == '\\') {
                        i++
                    } else if (c == quote) {
                        quote = null
                    }
                    c == '\'' || c == '"' || c == '`' -> quote = c
                    c == '(' || c == '{' || c == '[' -> depth++
                    c == ')' || c == '}' || c == ']' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
                i++
            }
            return null
        }

        private fun splitTopLevel(args: String): List<String> {
            val out = mutableListOf<String>()
            var depth = 0
            var quote: Char? = null
            var last = 0
            var i = 0
            while (i < args.length) {
                val c = args[i]
                when {
                    quote != null -> if (c == '\\') {
                        i++
                    } else if (c == quote) {
                        quote = null
                    }
                    c == '\'' || c == '"' || c == '`' -> quote = c
                    c == '(' || c == '{' || c == '[' -> depth++
                    c == ')' || c == '}' || c == ']' -> depth--
                    c == ',' && depth == 0 -> {
                        out += args.substring(last, i)
                        last = i + 1
                    }
                }
                i++
            }
            out += args.substring(last)
            return out.filter { it.isNotBlank() }
        }

        /** 최상위 `{ … }` 안에서 깊이 2 이상의 괄호 내부를 공백으로 가려 깊이-1 키만 남긴다. */
        private fun maskNested(body: String): String {
            val sb = StringBuilder(body.length)
            var depth = 0
            var quote: Char? = null
            for (c in body) {
                val opening = c == '(' || c == '{' || c == '['
                val closing = c == ')' || c == '}' || c == ']'
                when {
                    quote != null -> {
                        sb.append(' ')
                        if (c == quote) quote = null
                    }
                    c == '\'' || c == '"' || c == '`' -> {
                        quote = c
                        sb.append(' ')
                    }
                    opening -> {
                        depth++
                        sb.append(if (depth <= 1) c else ' ')
                    }
                    closing -> {
                        sb.append(if (depth <= 1) c else ' ')
                        depth--
                    }
                    else -> sb.append(if (depth <= 1) c else ' ')
                }
            }
            return sb.toString()
        }

        /**
         * 인라인 이벤트 핸들러 속성(`onclick="…"` 등)에 `${…}` 보간이 들어간 곳.
         *
         * `escapeHtml` 은 HTML 문맥 인코딩이라 JS 문자열 문맥을 지켜 주지 못한다 — 브라우저는 속성값의 `&#39;` 를 `'` 로 되돌린 뒤
         * 그 결과를 JavaScript 로 컴파일하므로 따옴표가 든 값은 핸들러를 탈출한다. 값은 `data-*`(HTML 속성 문맥) 로 넘기고
         * 클릭은 `addEventListener` 위임 리스너가 받아야 한다.
         */
        fun inlineHandlerInterpolations(html: String): List<Pair<String, Int>> =
            Regex("""\bon[a-z]+\s*=\s*(?:"[^"]*"|'[^']*')""").findAll(html)
                .filter { it.value.contains("\${") }
                .map { it.value to html.substring(0, it.range.first).count { ch -> ch == '\n' } + 1 }
                .toList()

        /** innerHTML 에 쓰이는 템플릿 리터럴 안에서 바인딩 변수를 escapeHtml 없이 끼워 넣은 곳. `textContent` 대입은 대상이 아니다. */
        fun unescapedInterpolations(script: String, variables: Set<String>): List<Pair<String, Int>> {
            val out = mutableListOf<Pair<String, Int>>()
            for (block in Regex("""innerHTML\s*=\s*([\s\S]*?);(?:\n|$)""").findAll(script)) {
                val body = block.groupValues[1]
                for (m in Regex("""\$\{([^{}]*)}""").findAll(body)) {
                    val expr = m.groupValues[1].trim()
                    val root = expr.substringBefore('.').substringBefore('(').substringBefore('[').trim()
                    if (root !in variables) continue
                    if (expr.contains("escapeHtml(")) continue
                    if (Regex("""^\w+\.(id|status)$""").containsMatchIn(expr)) continue // 숫자 식별자·HTTP 상태는 이스케이프 불필요
                    if (expr.contains("?") && !expr.contains("escapeHtml")) {
                        // 삼항이면 분기 값이 리터럴인지 본다 — 리터럴만이면 안전
                        val branches = expr.substringAfter("?").split(":").map { it.trim() }
                        if (branches.all { it.startsWith("'") || it.startsWith("\"") }) continue
                    }
                    out += expr to (block.range.first + m.range.first).let { idx -> script.substring(0, idx).count { it == '\n' } + 1 }
                }
            }
            return out
        }

        private fun jsonProperties(model: KClass<*>): Set<String> =
            model.memberProperties.mapNotNull { it.javaField?.getAnnotation(JsonProperty::class.java)?.value }.toSet()

        private fun candidates(prop: String, allowed: Set<String>): String =
            allowed.filter { it.lowercase().contains(prop.lowercase().take(3)) }.sorted().joinToString(", ").ifEmpty { "-" }
    }
}