package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.dto.auth.MfaCodeRequestDto
import com.rex.api.pub.skeleton.util.TotpCodes
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpStatus

/*
 * 2차 인증 e2e 공용 — 등록 API 로 TOTP 를 켜고 테스트용 인증 앱(TotpCodes)을 돌려준다.
 */

/** [accessToken] 의 사용자에게 TOTP 를 켠다 — setup → activate(첫 코드). 활성화는 이 토큰의 세션을 뺀 나머지 세션을 폐기한다. */
fun TestRestTemplate.enableMfa(accessToken: String): TotpCodes = enableMfaWithRecoveryCodes(accessToken).first

/** [enableMfa] 와 같고, 활성화 응답이 한 번만 주는 복구 코드 원본도 돌려준다. */
@Suppress("UNCHECKED_CAST")
fun TestRestTemplate.enableMfaWithRecoveryCodes(accessToken: String): Pair<TotpCodes, List<String>> {
    val setup = postWithAuth<Map<String, Any>>("/user/mfa/totp/setup", accessToken, emptyMap<String, Any>())
    setup.statusCode shouldBe HttpStatus.OK
    val codes = TotpCodes(setup.body.shouldNotBeNull()["secret"] as String)
    val activated = postWithAuth<Map<String, Any>>("/user/mfa/totp/activate", accessToken, MfaCodeRequestDto(codes.next()))
    activated.statusCode shouldBe HttpStatus.OK
    return codes to (activated.body.shouldNotBeNull()["recoveryCodes"] as List<String>)
}