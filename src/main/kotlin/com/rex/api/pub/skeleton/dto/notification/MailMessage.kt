package com.rex.api.pub.skeleton.dto.notification

/**
 * 발송할 메일 한 통 — [com.rex.api.pub.skeleton.service.notification.MailSender] 의 입력.
 */
data class MailMessage(
    val to: String,
    val subject: String,
    val body: String,
)