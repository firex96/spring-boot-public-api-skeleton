package com.rex.api.pub.skeleton.dto.notification

import java.time.LocalDateTime

/** [com.rex.api.pub.skeleton.service.notification.DemoInboxMailSender] 가 보관하는 수신 메일 한 통. */
data class ReceivedMailDto(
    val message: MailMessage,
    val receivedAt: LocalDateTime,
)