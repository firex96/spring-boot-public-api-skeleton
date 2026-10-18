package com.rex.api.pub.skeleton.support

import io.kotest.matchers.nulls.shouldNotBeNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 브라우저 하나를 흉내 낸다 — 세션 쿠키(`SKELETON_SESSION`)를 응답마다 갱신해 다음 요청에 싣고, 리다이렉트는 따르지 않는다(302·Location 을 단언한다).
 *
 * 인가 서버·소셜 로그인의 브라우저 흐름 e2e 가 함께 쓴다. 테스트마다(브라우저마다) 새로 만든다.
 */
class BrowserSession(
    private val rootUri: String,
) {
    /** 지금 들고 있는 세션 쿠키 — `SKELETON_SESSION=…`. 아직 없으면 null. */
    var cookie: String? = null
        private set

    fun get(path: String, bearer: String? = null): HttpResponse<String> =
        send(HttpRequest.newBuilder(URI.create(rootUri + path)).GET().apply { bearer?.let { header("Authorization", "Bearer $it") } })

    /** form-urlencoded POST. [basic] 이 있으면 `Authorization: Basic base64(urlencode(id):urlencode(secret))` (RFC 6749 §2.3.1). */
    fun postForm(path: String, form: Map<String, String>, basic: Pair<String, String>? = null): HttpResponse<String> {
        val body = form.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        return send(
            HttpRequest.newBuilder(URI.create(rootUri + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .apply {
                    basic?.let { (id, secret) ->
                        header("Authorization", "Basic " + Base64.getEncoder().encodeToString("${encode(id)}:${encode(secret)}".toByteArray()))
                    }
                }.POST(HttpRequest.BodyPublishers.ofString(body)),
        )
    }

    /** 서블릿 컨테이너는 Location 을 절대 URL 로 내보낸다 — 이 서버의 출처만 떼고(외부 redirect_uri 는 그대로) 돌려준다. */
    fun location(response: HttpResponse<*>): String = response.headers().firstValue("Location").orElse("").removePrefix(rootUri)

    /** 이 세션의 CSRF 토큰 — 로그인 화면 폼에 Thymeleaf 가 넣은 hidden `_csrf`. */
    fun csrf(): String = Regex("""name="_csrf"\s+value="([^"]+)"""").find(get("/oauth2/login").body()).shouldNotBeNull().groupValues[1]

    private fun send(builder: HttpRequest.Builder): HttpResponse<String> {
        cookie?.let { builder.header("Cookie", it) }
        val response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        response.headers().allValues("Set-Cookie").firstOrNull { it.startsWith("$COOKIE_NAME=") }?.let { cookie = it.substringBefore(";") }
        return response
    }

    companion object {
        private const val COOKIE_NAME = "SKELETON_SESSION"
        private val HTTP: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}