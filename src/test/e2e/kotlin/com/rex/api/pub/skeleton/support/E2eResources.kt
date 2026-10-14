package com.rex.api.pub.skeleton.support

import java.util.Collections

/**
 * spec 인스턴스별로 「이 실행이 만든 행」을 기록한다 — `E2eResourceJanitor.cleanup` 이 이 목록만 지운다.
 *
 * 등록은 생성 직후에 한다(등록 전에 단언이 실패하면 잔재가 된다). `audit_logs` 는 등록 대상이 아니다(사실 기록은 남긴다).
 */
class E2eResources {
    val userLoginIds: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())
    val groupIds: MutableSet<Long> = Collections.synchronizedSet(LinkedHashSet())
    val permissionIds: MutableSet<Long> = Collections.synchronizedSet(LinkedHashSet())
    val oauth2ClientIds: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())
    val blockedIps: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

    fun user(loginId: String): String = loginId.also { userLoginIds += it }

    fun group(id: Long): Long = id.also { groupIds += it }

    fun permission(id: Long): Long = id.also { permissionIds += it }

    fun oauth2Client(clientId: String): String = clientId.also { oauth2ClientIds += it }

    fun blockedIp(ip: String): String = ip.also { blockedIps += it }
}