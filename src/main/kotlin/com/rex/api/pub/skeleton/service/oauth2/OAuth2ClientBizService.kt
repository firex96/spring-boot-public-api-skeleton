package com.rex.api.pub.skeleton.service.oauth2

import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientWithSecretDto
import com.rex.api.pub.skeleton.dto.oauth2.UpdateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.oauth2.OAuth2ClientEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.oauth2.OAuth2TokenEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

/**
 * OAuth2 클라이언트 관리 — 등록·수정·시크릿 재발급·비활성·삭제와 클라이언트 자격 검증.
 * 시크릿은 해시로만 저장하고 평문은 생성·재발급 응답에 한 번만 나간다.
 */
@Service
class OAuth2ClientBizService(
    private val oAuth2ClientEntityService: OAuth2ClientEntityService,
    private val oAuth2TokenEntityService: OAuth2TokenEntityService,
    private val passwordEncoder: PasswordEncoder,
) {
    private val log = logger()

    companion object {
        private const val CLIENT_ID_LENGTH = 32
        private const val CLIENT_SECRET_LENGTH = 48
    }

    fun getAllClients(): List<OAuth2ClientDto> = oAuth2ClientEntityService.getAll()

    fun getActiveClients(): List<OAuth2ClientDto> = oAuth2ClientEntityService.getActive()

    fun getClientById(id: Long): OAuth2ClientDto = requireClient(id)

    fun getClientByClientId(clientId: String): OAuth2ClientDto =
        oAuth2ClientEntityService.getByClientId(clientId) ?: throw ResourceNotFoundException("클라이언트를 찾을 수 없습니다: $clientId")

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리의 @Modifying 은 auth 트랜잭션이 있어야 한다(primary 는 default TM)
    fun createClient(request: CreateOAuth2ClientRequestDto): OAuth2ClientWithSecretDto {
        val clientId = generateClientId()
        val clientSecret = generateClientSecret()
        val saved =
            oAuth2ClientEntityService.create(
                clientId = clientId,
                clientSecretHash = encodeSecret(clientSecret),
                clientName = request.clientName,
                description = request.description,
                grantTypes = request.grantTypes,
                redirectUris = request.redirectUris,
                scopes = request.scopes,
                accessTokenValidity = request.accessTokenValidity,
                refreshTokenValidity = request.refreshTokenValidity,
                isFirstParty = request.isFirstParty,
                requirePkce = request.requirePkce,
            )
        log.info("OAuth2 클라이언트 생성됨: ${saved.clientId}")
        return saved.withSecret(clientSecret)
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리의 @Modifying 은 auth 트랜잭션이 있어야 한다(primary 는 default TM)
    fun updateClient(id: Long, request: UpdateOAuth2ClientRequestDto): OAuth2ClientDto {
        requireClient(id)
        val saved =
            oAuth2ClientEntityService.update(
                id = id,
                clientName = request.clientName,
                description = request.description,
                grantTypes = request.grantTypes,
                redirectUris = request.redirectUris,
                scopes = request.scopes,
                accessTokenValidity = request.accessTokenValidity,
                refreshTokenValidity = request.refreshTokenValidity,
                isFirstParty = request.isFirstParty,
                requirePkce = request.requirePkce,
                isActive = request.isActive,
            )
        log.info("OAuth2 클라이언트 수정됨: ${saved.clientId}")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리의 @Modifying 은 auth 트랜잭션이 있어야 한다(primary 는 default TM)
    fun regenerateClientSecret(id: Long): OAuth2ClientWithSecretDto {
        requireClient(id)
        val newSecret = generateClientSecret()
        val saved = oAuth2ClientEntityService.updateSecretHash(id, encodeSecret(newSecret))
        log.info("OAuth2 클라이언트 시크릿 재발급됨: ${saved.clientId}")
        return saved.withSecret(newSecret)
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리의 @Modifying 은 auth 트랜잭션이 있어야 한다(primary 는 default TM)
    fun deleteClient(id: Long) {
        val client = requireClient(id)
        val now = LocalDateTime.now()
        oAuth2TokenEntityService.deleteAuthorizationCodesByClientId(client.clientId)
        oAuth2TokenEntityService.revokeAllByClientId(client.clientId, now)
        oAuth2ClientEntityService.delete(id)
        log.info("OAuth2 클라이언트 삭제됨: ${client.clientId}")
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리의 @Modifying 은 auth 트랜잭션이 있어야 한다(primary 는 default TM)
    fun deactivateClient(id: Long): OAuth2ClientDto {
        val client = requireClient(id)
        oAuth2TokenEntityService.revokeAllByClientId(client.clientId, LocalDateTime.now())
        val saved = oAuth2ClientEntityService.deactivate(id)
        log.info("OAuth2 클라이언트 비활성화됨: ${saved.clientId}")
        return saved
    }

    /** 활성 클라이언트의 시크릿이 일치하면 true — 클라이언트 자격 인증. */
    fun validateClientCredentials(clientId: String, clientSecret: String): Boolean {
        val secretHash = oAuth2ClientEntityService.getActiveClientSecretHash(clientId) ?: return false
        return passwordEncoder.matches(clientSecret, secretHash)
    }

    fun getActiveClient(clientId: String): OAuth2ClientDto? = oAuth2ClientEntityService.getActiveByClientId(clientId)

    private fun requireClient(id: Long): OAuth2ClientDto =
        oAuth2ClientEntityService.getById(id) ?: throw ResourceNotFoundException("클라이언트를 찾을 수 없습니다: $id")

    private fun generateClientId(): String {
        var clientId: String
        do {
            clientId = generateRandomString(CLIENT_ID_LENGTH)
        } while (oAuth2ClientEntityService.existsByClientId(clientId))
        return clientId
    }

    private fun generateClientSecret(): String = generateRandomString(CLIENT_SECRET_LENGTH)

    private fun generateRandomString(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).take(length)
    }

    private fun encodeSecret(plain: String): String =
        passwordEncoder.encode(plain) ?: throw IllegalStateException("PasswordEncoder 가 null 을 반환했다")

    private fun OAuth2ClientDto.withSecret(plainSecret: String) = OAuth2ClientWithSecretDto(
        id = id,
        clientId = clientId,
        clientSecret = plainSecret,
        clientName = clientName,
        description = description,
        grantTypes = grantTypes,
        redirectUris = redirectUris,
        scopes = scopes,
        accessTokenValidity = accessTokenValidity,
        refreshTokenValidity = refreshTokenValidity,
        isActive = isActive,
        isFirstParty = isFirstParty,
        requirePkce = requirePkce,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}