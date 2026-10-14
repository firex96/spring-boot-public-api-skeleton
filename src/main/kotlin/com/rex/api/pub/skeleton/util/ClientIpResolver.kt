package com.rex.api.pub.skeleton.util

import com.rex.api.pub.skeleton.config.security.SecurityProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.net.InetAddress

/**
 * 클라이언트 IP 주소를 안전하게 추출하는 유틸리티.
 *
 * X-Forwarded-For 헤더 스푸핑 공격을 방지하기 위해
 * 신뢰할 수 있는 프록시에서만 헤더를 신뢰합니다.
 */
@Component
class ClientIpResolver(
    private val securityProperties: SecurityProperties,
) {
    private val log = logger()

    companion object {
        private val FORWARDED_HEADERS = listOf(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
        )

        // 사설 IP 대역 (로드밸런서/프록시가 주로 사용)
        private val PRIVATE_IP_RANGES = listOf(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "127.0.0.0/8",
        )
    }

    /**
     * 클라이언트의 실제 IP 주소를 추출합니다.
     *
     * @param request HTTP 요청
     * @return 클라이언트 IP 주소
     */
    fun resolve(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr

        // 프록시 신뢰가 비활성화된 경우 직접 연결 IP 반환
        if (!shouldTrustProxy(remoteAddr)) {
            return remoteAddr
        }

        // X-Forwarded-For 헤더에서 클라이언트 IP 추출
        for (header in FORWARDED_HEADERS) {
            val headerValue = request.getHeader(header)
            if (!headerValue.isNullOrBlank()) {
                val clientIp = extractClientIp(headerValue)
                if (isValidIp(clientIp)) {
                    log.debug("Resolved client IP from $header: $clientIp (remote: $remoteAddr)")
                    return clientIp
                }
            }
        }

        return remoteAddr
    }

    /**
     * 프록시를 신뢰할지 여부를 결정합니다.
     */
    private fun shouldTrustProxy(remoteAddr: String): Boolean {
        // 개발 환경에서 모든 프록시 신뢰 (주의: 프로덕션에서 사용 금지)
        if (securityProperties.trustAllProxies) {
            return true
        }

        // 신뢰할 프록시 목록이 비어있으면 신뢰하지 않음
        if (securityProperties.trustedProxies.isEmpty()) {
            return false
        }

        // 신뢰할 프록시 목록에 있는지 확인 (CIDR 지원)
        return securityProperties.trustedProxies.any { trusted ->
            isIpInRange(remoteAddr, trusted)
        }
    }

    /**
     * X-Forwarded-For 헤더에서 첫 번째 (원본 클라이언트) IP를 추출합니다.
     * 형식: "client, proxy1, proxy2"
     */
    private fun extractClientIp(headerValue: String): String = headerValue.split(",")
        .firstOrNull()
        ?.trim()
        ?: headerValue.trim()

    /**
     * IP 주소가 유효한지 확인합니다.
     */
    private fun isValidIp(ip: String): Boolean {
        return try {
            val trimmed = ip.trim()
            if (trimmed.isBlank() || trimmed == "unknown") {
                return false
            }
            InetAddress.getByName(trimmed)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * IP가 CIDR 범위 내에 있는지 확인합니다.
     */
    private fun isIpInRange(ip: String, cidr: String): Boolean {
        return try {
            if (cidr.contains("/")) {
                val (network, prefixLength) = cidr.split("/")
                val networkAddress = InetAddress.getByName(network)
                val ipAddress = InetAddress.getByName(ip)

                val networkBytes = networkAddress.address
                val ipBytes = ipAddress.address

                if (networkBytes.size != ipBytes.size) {
                    return false
                }

                val prefix = prefixLength.toInt()
                val fullBytes = prefix / 8
                val remainingBits = prefix % 8

                // 전체 바이트 비교
                for (i in 0 until fullBytes) {
                    if (networkBytes[i] != ipBytes[i]) {
                        return false
                    }
                }

                // 남은 비트 비교
                if (remainingBits > 0 && fullBytes < networkBytes.size) {
                    val mask = (0xFF shl (8 - remainingBits)).toByte()
                    if ((networkBytes[fullBytes].toInt() and mask.toInt()) !=
                        (ipBytes[fullBytes].toInt() and mask.toInt())
                    ) {
                        return false
                    }
                }

                true
            } else {
                // CIDR이 아닌 경우 정확히 일치하는지 확인
                ip == cidr
            }
        } catch (e: Exception) {
            log.warn("Failed to check IP range: ip=$ip, cidr=$cidr", e)
            false
        }
    }
}