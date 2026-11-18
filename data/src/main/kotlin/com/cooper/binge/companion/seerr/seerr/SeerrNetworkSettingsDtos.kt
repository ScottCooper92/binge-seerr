package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong

/**
 * The network settings (`settings/network`, Jellyseerr 2.4+). Jellyseerr carries the two switches;
 * Seerr 3 adds the IPv4 preference, the outbound proxy and the DNS cache. A block the server did not
 * send stays null and is left out of the form and the body alike.
 */
@Serializable
data class SeerrNetworkSettingsDto(
    @SerialName("csrfProtection") val csrfProtection: Boolean? = null,
    @SerialName("trustProxy") val trustProxy: Boolean? = null,
    @SerialName("forceIpv4First") val forceIpv4First: Boolean? = null,
    @SerialName("proxy") val proxy: SeerrProxySettingsDto? = null,
    @SerialName("dnsCache") val dnsCache: SeerrDnsCacheSettingsDto? = null,
    /** How long the server waits on Radarr, Sonarr and the like, in milliseconds; 0 waits for ever. Seerr only. */
    @Serializable(with = WholeMillisecondsSerializer::class)
    @SerialName("apiRequestTimeout")
    val apiRequestTimeout: Long? = null,
)

/**
 * Milliseconds read as a JSON number of any shape and written as a whole one. The web client multiplies seconds in a
 * JS number, so it can store `1100.0000000000002` for 1.1 seconds, which a plain [Long] cannot decode.
 */
internal object WholeMillisecondsSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("WholeMilliseconds", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val number = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
        return number?.doubleOrNull?.roundToLong() ?: error("Expected a number of milliseconds")
    }

    override fun serialize(
        encoder: Encoder,
        value: Long,
    ) = encoder.encodeLong(value)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrProxySettingsDto(
    @EncodeDefault @SerialName("enabled") val enabled: Boolean = false,
    @EncodeDefault @SerialName("hostname") val hostname: String = "",
    @EncodeDefault @SerialName("port") val port: Int = 8080,
    @EncodeDefault @SerialName("useSsl") val useSsl: Boolean = false,
    @EncodeDefault @SerialName("user") val user: String = "",
    @EncodeDefault @SerialName("password") val password: String = "",
    @EncodeDefault @SerialName("bypassFilter") val bypassFilter: String = "",
    @EncodeDefault @SerialName("bypassLocalAddresses") val bypassLocalAddresses: Boolean = true,
)

/** The DNS cache: on or off, and the TTL bounds it forces (`-1` for none). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrDnsCacheSettingsDto(
    @EncodeDefault @SerialName("enabled") val enabled: Boolean = false,
    @EncodeDefault @SerialName("forceMinTtl") val forceMinTtl: Int = 0,
    @EncodeDefault @SerialName("forceMaxTtl") val forceMaxTtl: Int = -1,
)
