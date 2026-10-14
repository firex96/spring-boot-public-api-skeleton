package com.rex.api.pub.skeleton.persistence.jpa.service.oauth2

import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientDto
import com.rex.api.pub.skeleton.mapper.OAuth2Mapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2ClientEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2ClientRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/**
 * OAuth2 클라이언트 엔티티 서비스. 조회는 `OAuth2ClientDto`(시크릿 없음), 시크릿 해시는 `getActiveClientSecretHash` 로만 읽는다.
 * CSV 컬럼(grantTypes·redirectUris·scopes)의 결합은 여기서, 분리는 매퍼(`splitCsv`)가 한다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class OAuth2ClientEntityService(
    private val oAuth2ClientRepository: OAuth2ClientRepository,
    private val mapper: OAuth2Mapper,
) {
    fun getAll(): List<OAuth2ClientDto> = mapper.toClientDtoList(oAuth2ClientRepository.findAll())

    fun getActive(): List<OAuth2ClientDto> = mapper.toClientDtoList(oAuth2ClientRepository.findByIsActiveTrue())

    fun getById(id: Long): OAuth2ClientDto? = oAuth2ClientRepository.findByIdOrNull(id)?.let { mapper.toClientDto(it) }

    fun getByClientId(clientId: String): OAuth2ClientDto? =
        oAuth2ClientRepository.findByClientId(clientId).getOrNull()?.let { mapper.toClientDto(it) }

    fun getActiveByClientId(clientId: String): OAuth2ClientDto? =
        oAuth2ClientRepository.findByClientIdAndIsActiveTrue(clientId).getOrNull()?.let { mapper.toClientDto(it) }

    fun getActiveClientSecretHash(clientId: String): String? =
        oAuth2ClientRepository.findByClientIdAndIsActiveTrue(clientId).getOrNull()?.clientSecret

    fun existsByClientId(clientId: String): Boolean = oAuth2ClientRepository.existsByClientId(clientId)

    @Transactional(transactionManager = "authTransactionManager")
    fun create(
        clientId: String,
        clientSecretHash: String,
        clientName: String,
        description: String?,
        grantTypes: List<String>,
        redirectUris: List<String>,
        scopes: List<String>,
        accessTokenValidity: Int,
        refreshTokenValidity: Int,
        isFirstParty: Boolean,
        requirePkce: Boolean,
    ): OAuth2ClientDto {
        val client =
            OAuth2ClientEntity(
                clientId = clientId,
                clientSecret = clientSecretHash,
                clientName = clientName,
                description = description,
                grantTypes = grantTypes.joinToString(","),
                redirectUris = redirectUris.joinToString(",").ifEmpty { null },
                scopes = scopes.joinToString(","),
                accessTokenValidity = accessTokenValidity,
                refreshTokenValidity = refreshTokenValidity,
                isFirstParty = isFirstParty,
                requirePkce = requirePkce,
            )
        return mapper.toClientDto(oAuth2ClientRepository.save(client))
    }

    /** null 인 인자는 유지한다(`clientName` 만 필수). */
    @Transactional(transactionManager = "authTransactionManager")
    fun update(
        id: Long,
        clientName: String,
        description: String?,
        grantTypes: List<String>?,
        redirectUris: List<String>?,
        scopes: List<String>?,
        accessTokenValidity: Int?,
        refreshTokenValidity: Int?,
        isFirstParty: Boolean?,
        requirePkce: Boolean?,
        isActive: Boolean?,
    ): OAuth2ClientDto {
        val client = requireClient(id)
        client.clientName = clientName
        description?.let { client.description = it }
        grantTypes?.let { client.grantTypes = it.joinToString(",") }
        redirectUris?.let { client.redirectUris = it.joinToString(",").ifEmpty { null } }
        scopes?.let { client.scopes = it.joinToString(",") }
        accessTokenValidity?.let { client.accessTokenValidity = it }
        refreshTokenValidity?.let { client.refreshTokenValidity = it }
        isFirstParty?.let { client.isFirstParty = it }
        requirePkce?.let { client.requirePkce = it }
        isActive?.let { client.isActive = it }
        client.updatedAt = LocalDateTime.now()
        return mapper.toClientDto(oAuth2ClientRepository.save(client))
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun updateSecretHash(id: Long, clientSecretHash: String): OAuth2ClientDto {
        val client = requireClient(id)
        client.clientSecret = clientSecretHash
        client.updatedAt = LocalDateTime.now()
        return mapper.toClientDto(oAuth2ClientRepository.save(client))
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun deactivate(id: Long): OAuth2ClientDto {
        val client = requireClient(id)
        client.isActive = false
        client.updatedAt = LocalDateTime.now()
        return mapper.toClientDto(oAuth2ClientRepository.save(client))
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun delete(id: Long) {
        oAuth2ClientRepository.deleteById(id)
    }

    private fun requireClient(id: Long): OAuth2ClientEntity =
        oAuth2ClientRepository.findByIdOrNull(id) ?: throw NoSuchElementException("클라이언트가 없습니다: $id") // 호출자가 먼저 존재를 확인한다
}