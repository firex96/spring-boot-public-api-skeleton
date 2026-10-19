package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

data class MfaSetupModel(
    @field:JsonProperty("secret")
    val secret: String,
    @field:JsonProperty("otpauthUri")
    val otpauthUri: String,
)