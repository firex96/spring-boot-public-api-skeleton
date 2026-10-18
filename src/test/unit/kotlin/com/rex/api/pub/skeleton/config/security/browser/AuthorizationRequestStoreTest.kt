package com.rex.api.pub.skeleton.config.security.browser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest

/** 로그인 뒤 돌아갈 곳은 서버가 조립한 authorize URL 뿐이고, 한 번만 쓴다 — 오픈 리다이렉트 표면을 두지 않는다. */
class AuthorizationRequestStoreTest : BehaviorSpec({
    val store = AuthorizationRequestStore()

    Given("AR-1 authorize URL") {
        Then("저장하고 한 번만 꺼낸다") {
            val request = MockHttpServletRequest()
            store.save(request, "/oauth2/authorize?response_type=code&client_id=c")

            store.take(request) shouldBe "/oauth2/authorize?response_type=code&client_id=c"
            store.take(request).shouldBeNull()
        }
    }

    Given("AR-2 authorize 가 아닌 URL") {
        Then("저장을 거부한다") {
            listOf("https://evil.example/", "//evil.example", "/admin", "/oauth2/authorizeX?a=1").forEach { url ->
                shouldThrow<IllegalArgumentException> { store.save(MockHttpServletRequest(), url) }
            }
        }
    }

    Given("AR-3 세션이 없는 요청") {
        Then("null 이다") {
            store.take(MockHttpServletRequest()).shouldBeNull()
        }
    }
})