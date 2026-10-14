package com.rex.api.pub.skeleton.service.oidc

import com.rex.api.pub.skeleton.config.security.OidcProperties
import com.rex.api.pub.skeleton.util.logger
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Service
class JwkBizService(
    private val oidcProperties: OidcProperties,
) {
    private val log = logger()

    private lateinit var keyPair: KeyPair
    private lateinit var keyId: String

    @PostConstruct
    fun init() {
        keyId = oidcProperties.keyId
        keyPair = loadOrGenerateKeyPair()
        log.info("JWK 초기화 완료 - Key ID: $keyId")
    }

    fun getPublicKey(): RSAPublicKey = keyPair.public as RSAPublicKey

    fun getPrivateKey(): RSAPrivateKey = keyPair.private as RSAPrivateKey

    fun getKeyId(): String = keyId

    fun getJwkSet(): Map<String, Any> {
        val publicKey = getPublicKey()
        val encoder = Base64.getUrlEncoder().withoutPadding()

        val jwk = mapOf(
            "kty" to "RSA",
            "use" to "sig",
            "alg" to "RS256",
            "kid" to keyId,
            "n" to encoder.encodeToString(publicKey.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()),
            "e" to encoder.encodeToString(publicKey.publicExponent.toByteArray()),
        )

        return mapOf("keys" to listOf(jwk))
    }

    private fun loadOrGenerateKeyPair(): KeyPair {
        val privateKeyPem = oidcProperties.rsaPrivateKey
        val publicKeyPem = oidcProperties.rsaPublicKey

        return if (!privateKeyPem.isNullOrBlank() && !publicKeyPem.isNullOrBlank()) {
            loadKeyPairFromPem(privateKeyPem, publicKeyPem)
        } else {
            // 프로덕션 환경에서는 RSA 키 필수
            if (oidcProperties.requireRsaKey) {
                throw IllegalStateException(
                    "RSA 키가 설정되지 않았습니다. 프로덕션 환경에서는 oidc.rsa-private-key와 oidc.rsa-public-key를 반드시 설정해야 합니다.",
                )
            }
            generateKeyPair()
        }
    }

    private fun loadKeyPairFromPem(privateKeyPem: String, publicKeyPem: String): KeyPair {
        val keyFactory = KeyFactory.getInstance("RSA")

        val privateKeyBytes = Base64.getDecoder().decode(
            privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), ""),
        )
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes)) as RSAPrivateKey

        val publicKeyBytes = Base64.getDecoder().decode(
            publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), ""),
        )
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as RSAPublicKey

        log.info("PEM에서 RSA 키 로드 완료")
        return KeyPair(publicKey, privateKey)
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        log.warn("RSA 키가 설정되지 않아 임시 키를 생성했습니다. 프로덕션에서는 고정 키를 설정하세요.")
        return keyPair
    }
}