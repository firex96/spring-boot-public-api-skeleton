package com.rex.api.pub.skeleton.client.social

import com.rex.api.pub.skeleton.config.security.OAuth2Properties
import com.rex.api.pub.skeleton.config.security.OAuth2ProviderProperties
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** 공급자의 오류 응답 문구(`error_description`·`message`)는 로그에만 남고 업무 예외 메시지(응답 `reason`)에는 실리지 않는다. */
class NaverOAuth2ClientTest : DescribeSpec({

    val properties = OAuth2Properties(naver = OAuth2ProviderProperties(clientId = "client", clientSecret = "secret"))

    describe("getAccessToken") {
        it("토큰 응답이 오류면 공급자 문구 없이 고정 문장으로 실패한다") {
            val client = NaverOAuth2Client(properties, stubWebClient("""{"error":"invalid_grant","error_description":"bad code https://internal.example"}"""))

            val exception = shouldThrow<AuthenticationException> { client.getAccessToken("code", "state") }

            exception.message shouldBe "네이버 토큰 발급에 실패했습니다."
        }
    }

    describe("getUserInfo") {
        it("사용자 정보 응답이 실패 코드면 공급자 문구 없이 고정 문장으로 실패한다") {
            val client = NaverOAuth2Client(properties, stubWebClient("""{"resultcode":"024","message":"Authentication failed https://internal.example","response":null}"""))

            val exception = shouldThrow<AuthenticationException> { client.getUserInfo("token") }

            exception.message shouldBe "네이버 사용자 정보 조회에 실패했습니다."
        }
    }
})