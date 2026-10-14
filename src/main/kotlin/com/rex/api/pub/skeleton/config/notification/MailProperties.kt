package com.rex.api.pub.skeleton.config.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 메일 발송 설정 (`app.mail.*`).
 *
 * @property demoMailboxDir [com.rex.api.pub.skeleton.service.notification.DemoInboxMailSender] 가 수신 메일을 파일로 떨어뜨릴 디렉터리.
 *   `local` 프로파일만 값을 둔다(`~/.skeleton/demo-mailbox`) — 개발자 머신의 소유자 전용(0700/0600) 파일이라 네트워크·로그로 노출되지 않는다.
 *   비어 있으면 파일을 쓰지 않는다(e2e 는 인메모리 수신함만 쓴다).
 */
@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(
    val demoMailboxDir: String? = null,
)