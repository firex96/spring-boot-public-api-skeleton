package com.rex.api.pub.skeleton.service.notification

import com.rex.api.pub.skeleton.dto.notification.MailMessage
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Component

/**
 * [MailSender] 기본 구현 — 실제로 보내지 않고 **수신자(마스킹)·제목만** INFO 로그에 남기며, 던지지 않는다.
 *
 * 본문은 어떤 프로파일에서도 로그에 남기지 않는다 — 비밀번호 재설정·이메일 인증 토큰이 들어 있어 로그가 곧 토큰 유출 경로가 된다.
 * 로컬 데모·e2e 가 토큰을 받는 길은 [DemoInboxMailSender](`local`·`e2e` 프로파일에서만 뜨는 수신함 — e2e 는 빈 주입, local 은 소유자 전용 파일)다.
 */
@Component
class LoggingMailSender : MailSender {
    private val log = logger()

    override fun send(message: MailMessage) {
        log.info("메일 발송(로깅 구현): to=${maskRecipient(message.to)}, subject=${message.subject}")
    }

    companion object {
        /** `ab***@example.com` — 로컬 파트 앞 2자만 남긴다(PII 를 로그에 통째로 남기지 않는다). */
        fun maskRecipient(email: String): String {
            val at = email.indexOf('@')
            if (at <= 0) return "***"
            val local = email.substring(0, at)
            return local.take(2) + "***" + email.substring(at)
        }
    }
}