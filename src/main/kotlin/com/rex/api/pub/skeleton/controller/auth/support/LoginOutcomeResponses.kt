package com.rex.api.pub.skeleton.controller.auth.support

import com.rex.api.pub.skeleton.model.auth.LoginOutcomeModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/** API 로그인 결과의 HTTP 응답 — 토큰은 200, 2차 인증 challenge 는 202. 비밀번호·네이티브 소셜 로그인이 같은 계약을 쓴다. */
fun LoginOutcomeModel.toResponseEntity(): ResponseEntity<Any> =
    when (this) {
        is LoginOutcomeModel.Tokens -> ResponseEntity.ok(tokens)
        is LoginOutcomeModel.MfaRequired -> ResponseEntity.status(HttpStatus.ACCEPTED).body(challenge)
    }