package com.rex.api.pub.skeleton.service.notification

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.rex.api.pub.skeleton.dto.notification.MailMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.LoggerFactory

/**
 * [LoggingMailSender] 의 로그 계약 — 수신자는 마스킹, 제목은 그대로, **본문(토큰)은 어떤 설정에서도 남기지 않는다**.
 */
class LoggingMailSenderTest : BehaviorSpec({
    val logger = LoggerFactory.getLogger(LoggingMailSender::class.java) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    val sender = LoggingMailSender()

    beforeSpec {
        logger.level = Level.INFO
        logger.addAppender(appender)
    }
    afterSpec { logger.detachAppender(appender) }
    beforeTest { appender.list.clear() }

    Given("토큰이 든 메일") {
        val message = MailMessage(to = "alice@example.test", subject = "제목", body = "본문토큰XYZ")

        When("send 하면") {
            Then("M-1 INFO 한 줄 — 수신자는 마스킹(al***@example.test), 제목은 있고, 본문·원본 주소는 없다") {
                sender.send(message)
                appender.list.size shouldBe 1
                appender.list.single().level shouldBe Level.INFO
                val line = appender.list.single().formattedMessage
                line shouldContain "al***@example.test"
                line shouldContain "제목"
                line shouldNotContain "본문토큰XYZ"
                line shouldNotContain "alice@example.test"
            }
        }
    }

    Given("마스킹 규칙") {
        Then("M-2 로컬 파트 앞 2자만 남기고, @ 가 없으면 전부 가린다") {
            LoggingMailSender.maskRecipient("bob@x.io") shouldBe "bo***@x.io"
            LoggingMailSender.maskRecipient("a@x.io") shouldBe "a***@x.io"
            LoggingMailSender.maskRecipient("no-at-sign") shouldBe "***"
        }
    }
})