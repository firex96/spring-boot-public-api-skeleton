package com.rex.api.pub.skeleton.service.notification

import com.rex.api.pub.skeleton.config.notification.MailProperties
import com.rex.api.pub.skeleton.dto.notification.MailMessage
import com.rex.api.pub.skeleton.dto.notification.ReceivedMailDto
import com.rex.api.pub.skeleton.util.logger
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 데모·e2e 용 수신함 — **`local`·`e2e` 프로파일에서만** 뜨고 `@Primary` 로 [LoggingMailSender] 를 이긴다.
 *
 * - 인메모리: 최근 [CAPACITY]통을 들고 [latest] 로 돌려준다 — e2e 가 빈을 직접 주입받아 읽는다(HTTP 표면 없음).
 * - 파일: `app.mail.demo-mailbox-dir` 가 있으면(`local` 만) 수신자별 파일 `<디렉터리>/<수신자>.txt` 로도 남긴다 —
 *   개발자 머신의 소유자 전용 파일(디렉터리 0700, 파일 0600 — 있을 때도 매번 강제, 심볼릭 링크 거절)이라 브라우저 데모가 토큰을 읽는 유일한 길이 네트워크도 로그도 아니다.
 *   파일 기록 실패는 [MailSender] 계약대로 예외로 전파된다(토큰 저장 롤백).
 *
 * 수신함을 HTTP 로 열지 않는 이유: 토큰이 든 메일을 수신자 외에 누구나 읽을 수 있는 표면은 그 자체가 계정 탈취 오라클이다(프로파일 제한은 접근 통제가 아니다).
 */
@Component
@Primary
@Profile("local", "e2e")
class DemoInboxMailSender(
    mailProperties: MailProperties,
) : MailSender {
    private val log = logger()
    private val inbox = ConcurrentLinkedDeque<ReceivedMailDto>()
    private val mailboxDir: Path? = mailProperties.demoMailboxDir?.takeIf { it.isNotBlank() }?.let { Path.of(it) }

    override fun send(message: MailMessage) {
        val received = ReceivedMailDto(message = message, receivedAt = LocalDateTime.now())
        inbox.addFirst(received)
        while (inbox.size > CAPACITY) inbox.pollLast()
        mailboxDir?.let { writeMailbox(it, received) }
        log.info("메일 수신함(데모): to=${LoggingMailSender.maskRecipient(message.to)}, subject=${message.subject}")
    }

    /** 수신자에게 온 가장 최근 메일 — 없으면 null. */
    fun latest(to: String): ReceivedMailDto? = inbox.firstOrNull { it.message.to.equals(to, ignoreCase = true) }

    fun size(): Int = inbox.size

    /**
     * 수신자별 파일로 남긴다 — **실패는 전파**한다([MailSender] 계약: 던지면 호출자 트랜잭션이 롤백되어 받을 수 없는 토큰이 DB 에 남지 않는다).
     * 디렉터리·파일은 있을 때도 매번 소유자 전용 권한을 강제하고, 심볼릭 링크·일반 파일 아님은 거절한다(`NOFOLLOW_LINKS`).
     * Spring 의 기본 롤백 규칙은 RuntimeException 만 보므로 IOException 은 IllegalStateException 으로 감싼다.
     */
    private fun writeMailbox(
        dir: Path,
        received: ReceivedMailDto,
    ) {
        try {
            if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                check(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) { "데모 수신함 경로가 디렉터리가 아니다(심볼릭 링크 포함): $dir" }
            } else {
                Files.createDirectories(dir)
            }
            Files.setPosixFilePermissions(dir, DIR_PERMISSIONS)

            val file = dir.resolve(fileNameFor(received.message.to))
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "데모 수신함 파일이 일반 파일이 아니다(심볼릭 링크 포함): $file" }
            } else {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
            }
            Files.setPosixFilePermissions(file, FILE_PERMISSIONS)
            Files.writeString(
                file,
                "To: ${received.message.to}\nSubject: ${received.message.subject}\nReceived-At: ${received.receivedAt}\n\n${received.message.body}\n",
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        } catch (e: IOException) {
            throw IllegalStateException("데모 수신함 파일 기록 실패: dir=$dir", e)
        }
    }

    companion object {
        const val CAPACITY = 100
        private val DIR_PERMISSIONS = PosixFilePermissions.fromString("rwx------")
        private val FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------")
        private const val NAME_PREFIX_LENGTH = 24
        private const val DIGEST_LENGTH = 16

        /**
         * 수신자 주소 → 파일명. 사람이 읽을 접두(소문자·`[a-z0-9@._-]` 외 `_`, 24자) + 주소 전체의 SHA-256 앞 16 hex — 접두가 같아지는
         * 주소(`a+b@x` 와 `a_b@x`)도 다른 파일로 간다.
         */
        fun fileNameFor(to: String): String {
            val normalized = to.lowercase()
            val readable = normalized.replace(Regex("[^a-z0-9@._-]"), "_").take(NAME_PREFIX_LENGTH)
            val digest =
                MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }.take(DIGEST_LENGTH)
            return "$readable-$digest.txt"
        }
    }
}