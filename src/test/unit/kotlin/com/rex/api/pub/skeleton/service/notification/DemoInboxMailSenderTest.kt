package com.rex.api.pub.skeleton.service.notification

import com.rex.api.pub.skeleton.config.notification.MailProperties
import com.rex.api.pub.skeleton.dto.notification.MailMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.readText

/**
 * [DemoInboxMailSender] — 수신자별 최근 메일, 용량 [DemoInboxMailSender.CAPACITY], `local` 의 파일 수신함(소유자 전용 권한).
 */
class DemoInboxMailSenderTest : BehaviorSpec({
    val memoryOnly = MailProperties(demoMailboxDir = null)

    Given("메일 둘이 같은 수신자에게 차례로 오면") {
        val inbox = DemoInboxMailSender(memoryOnly)
        inbox.send(MailMessage(to = "alice@example.test", subject = "첫째", body = "1"))
        inbox.send(MailMessage(to = "Alice@example.test", subject = "둘째", body = "2"))

        Then("D-1 latest 는 가장 최근 것이고 수신자 비교는 대소문자를 무시한다") {
            inbox.latest("alice@example.test").shouldNotBeNull().message.subject shouldBe "둘째"
        }

        Then("D-2 다른 수신자는 null") {
            inbox.latest("bob@example.test").shouldBeNull()
        }
    }

    Given("용량을 넘게 보내면") {
        val inbox = DemoInboxMailSender(memoryOnly)
        repeat(DemoInboxMailSender.CAPACITY + 5) { i -> inbox.send(MailMessage(to = "u$i@example.test", subject = "s", body = "b")) }

        Then("D-3 최근 CAPACITY 통만 남고 가장 오래된 것은 밀려난다") {
            inbox.size() shouldBe DemoInboxMailSender.CAPACITY
            inbox.latest("u0@example.test").shouldBeNull()
            inbox.latest("u${DemoInboxMailSender.CAPACITY + 4}@example.test").shouldNotBeNull()
        }
    }

    Given("demo-mailbox-dir 가 설정된 local 수신함") {
        val dir = Files.createTempDirectory("demo-mailbox-test").resolve("mailbox")
        val inbox = DemoInboxMailSender(MailProperties(demoMailboxDir = dir.toString()))
        inbox.send(MailMessage(to = "Carol@Example.test", subject = "재설정", body = "token=RAW-1"))
        val file = dir.resolve(DemoInboxMailSender.fileNameFor("carol@example.test"))

        Then("D-4 수신자 파일이 소유자 전용 권한으로 생기고 본문을 담는다 — 디렉터리도 소유자 전용") {
            Files.exists(file) shouldBe true
            file.readText() shouldContain "token=RAW-1"
            file.readText() shouldContain "Subject: 재설정"
            Files.getPosixFilePermissions(file) shouldBe setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            Files.getPosixFilePermissions(dir) shouldBe
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        }

        Then("D-5 같은 수신자에게 다시 오면 파일은 최근 것으로 덮인다") {
            inbox.send(MailMessage(to = "carol@example.test", subject = "두 번째", body = "token=RAW-2"))
            file.readText() shouldContain "token=RAW-2"
            file.readText() shouldNotContain "token=RAW-1"
        }

        Then("D-7 느슨해진 기존 디렉터리·파일 권한은 다음 send 때 다시 소유자 전용으로 강제된다") {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
            inbox.send(MailMessage(to = "carol@example.test", subject = "셋째", body = "token=RAW-3"))
            Files.getPosixFilePermissions(dir) shouldBe
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
            Files.getPosixFilePermissions(file) shouldBe setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        }

        Then("D-8 수신자 파일 자리에 심볼릭 링크가 있으면 쓰지 않고 IllegalStateException") {
            val target = Files.createTempFile("demo-mailbox-target", ".txt")
            val linkedName = DemoInboxMailSender.fileNameFor("dave@example.test")
            Files.createSymbolicLink(dir.resolve(linkedName), target)
            shouldThrow<IllegalStateException> { inbox.send(MailMessage(to = "dave@example.test", subject = "s", body = "token=RAW-9")) }
            target.readText() shouldNotContain "RAW-9"
        }
    }

    Given("demo-mailbox-dir 자리에 일반 파일이 있어 디렉터리를 만들 수 없으면") {
        val blocker = Files.createTempFile("demo-mailbox-blocker", ".txt")
        val inbox = DemoInboxMailSender(MailProperties(demoMailboxDir = blocker.toString()))

        Then("D-9 기록 실패는 IllegalStateException 으로 전파된다 (MailSender 계약 — 호출자 트랜잭션 롤백)") {
            shouldThrow<IllegalStateException> { inbox.send(MailMessage(to = "erin@example.test", subject = "s", body = "b")) }
        }
    }

    Given("파일명 규칙") {
        Then("D-6 읽을 수 있는 접두 + 주소 digest — 접두가 같아지는 주소도 다른 파일") {
            val name = DemoInboxMailSender.fileNameFor("A.B+c@Example.test")
            name.startsWith("a.b_c@example.test-") shouldBe true
            name.endsWith(".txt") shouldBe true
            DemoInboxMailSender.fileNameFor("a+b@example.test") shouldNotBe DemoInboxMailSender.fileNameFor("a_b@example.test")
            DemoInboxMailSender.fileNameFor("Carol@Example.test") shouldBe DemoInboxMailSender.fileNameFor("carol@example.test")
        }
    }
})