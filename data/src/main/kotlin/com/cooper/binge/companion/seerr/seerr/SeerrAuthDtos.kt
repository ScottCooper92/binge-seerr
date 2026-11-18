package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeerrJellyfinLoginBody(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
)

@Serializable
data class SeerrLocalLoginBody(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

@Serializable
data class SeerrPlexLoginBody(
    @SerialName("authToken") val authToken: String,
)

@Serializable
data class SeerrQuickConnectDto(
    @SerialName("code") val code: String,
    @SerialName("secret") val secret: String,
)

@Serializable
data class SeerrQuickConnectCheckDto(
    @SerialName("authenticated") val authenticated: Boolean = false,
)

@Serializable
data class SeerrQuickConnectSecretBody(
    @SerialName("secret") val secret: String,
)

@Serializable
data class SeerrPasswordResetBody(
    @SerialName("email") val email: String,
)
