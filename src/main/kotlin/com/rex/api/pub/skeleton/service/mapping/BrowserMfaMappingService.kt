package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.model.auth.BrowserMfaResultModel
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.service.auth.TokenHandoffBizService
import com.rex.api.pub.skeleton.service.mfa.MfaChallengeBizService
import org.springframework.stereotype.Service

/**
 * 브라우저 2단계 화면(`/auth/mfa`) 매핑 — 인가 서버 로그인·소셜 콜백이 만든 challenge 만 받는다(출처 검사는 `MfaChallengeBizService.complete`).
 */
@Service
class BrowserMfaMappingService(
    private val mfaChallengeBizService: MfaChallengeBizService,
    private val authBizService: AuthBizService,
    private val tokenHandoffBizService: TokenHandoffBizService,
    private val authMapper: AuthMapper,
) {
    /** 화면을 보여 줄 challenge 가 살아 있는가. */
    fun isPending(mfaToken: String): Boolean = mfaChallengeBizService.isPending(mfaToken, MfaOrigin.BROWSER)

    /**
     * 2단계 완료 — 세션을 발급하고 브라우저 principal 을 만든다. 소셜 콜백 출처면 토큰을 교환 대기에 맡긴다(페이지가 `POST /auth/session/token` 으로 받는다).
     */
    fun complete(mfaToken: String, method: MfaMethod, code: String, ipAddress: String?): BrowserMfaResultModel {
        val completion = mfaChallengeBizService.complete(mfaToken, MfaOrigin.BROWSER, method, code, ipAddress)
        val principal = authBizService.browserPrincipalOf(completion.session)
        if (completion.origin == MfaOrigin.SOCIAL_BROWSER) {
            tokenHandoffBizService.put(completion.session.sessionId, completion.session.tokens)
        }
        return BrowserMfaResultModel(principal = authMapper.toBrowserPrincipalModel(principal), origin = completion.origin)
    }
}