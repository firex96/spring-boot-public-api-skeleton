package com.rex.api.pub.skeleton.service.notification

import com.rex.api.pub.skeleton.dto.notification.MailMessage

/**
 * 메일 발송 포트.
 *
 * 스켈레톤의 기본 구현은 [LoggingMailSender](로그만 남긴다)다. 실제 발송(SMTP·SES 등)은 파생 프로젝트가 이 인터페이스의
 * 구현 빈을 하나 더 두고 `@Primary` 로 올린다 — 계정·발신 도메인은 파생의 결정이라 스켈레톤이 정하지 않는다.
 *
 * 실패 계약: 보낼 수 없으면 **RuntimeException 을 던진다**(Spring 기본 롤백 규칙은 checked 예외를 보지 않는다 — IOException 은 감싼다). 호출자(비밀번호 재설정·이메일 인증)는 같은 트랜잭션 안에서 토큰을 저장했으므로
 * 예외가 전파되어 토큰 저장이 롤백된다 — **DB 에 남는 토큰은 전부 발송된 것**이다. 반대 방향(발송 뒤 커밋 실패)은 메일 속 토큰이
 * DB 에 없어 무효가 될 뿐이라 재요청으로 끝난다(보안 영향 없음). 정확히-한-번 배달이 필요하면(outbox 테이블 + 재시도 워커) 파생 프로젝트의 결정이다.
 */
interface MailSender {
    fun send(message: MailMessage)
}