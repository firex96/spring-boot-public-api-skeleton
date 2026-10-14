package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.UpdateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.mapper.OAuth2Mapper
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientWithSecretModel
import com.rex.api.pub.skeleton.service.oauth2.OAuth2ClientBizService
import org.springframework.stereotype.Service

/** OAuth2 클라이언트 관리 API 매핑 — `AdminOAuth2ClientController`. */
@Service
class OAuth2ClientMappingService(
    private val oAuth2ClientBizService: OAuth2ClientBizService,
    private val mapper: OAuth2Mapper,
) {
    fun getAllClients(): List<OAuth2ClientModel> = mapper.toClientModelList(oAuth2ClientBizService.getAllClients())

    fun getActiveClients(): List<OAuth2ClientModel> = mapper.toClientModelList(oAuth2ClientBizService.getActiveClients())

    fun getClient(id: Long): OAuth2ClientModel = mapper.toClientModel(oAuth2ClientBizService.getClientById(id))

    fun createClient(request: CreateOAuth2ClientRequestDto): OAuth2ClientWithSecretModel =
        mapper.toClientWithSecretModel(oAuth2ClientBizService.createClient(request))

    fun updateClient(id: Long, request: UpdateOAuth2ClientRequestDto): OAuth2ClientModel =
        mapper.toClientModel(oAuth2ClientBizService.updateClient(id, request))

    fun regenerateSecret(id: Long): OAuth2ClientWithSecretModel = mapper.toClientWithSecretModel(oAuth2ClientBizService.regenerateClientSecret(id))

    fun deactivateClient(id: Long): OAuth2ClientModel = mapper.toClientModel(oAuth2ClientBizService.deactivateClient(id))

    fun deleteClient(id: Long) {
        oAuth2ClientBizService.deleteClient(id)
    }
}