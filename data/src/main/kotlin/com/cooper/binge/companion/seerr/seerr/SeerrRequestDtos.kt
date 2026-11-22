package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.serializer

/**
 * A media request. The advanced-option overrides are omitted when absent, so a plain request posts
 * exactly as one and the server applies its per-instance defaults. `is4k` is not one of those
 * overrides — it's a flag the server always reads off the body, so it stays encoded even at its
 * `false` default (as [SeerrMediaStatusBody.is4k] already does).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrRequestBody(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("mediaId") val mediaId: Int,
    @SerialName("seasons") val seasons: List<Int>? = null,
    @EncodeDefault @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
    /** Sent once the user has touched the tags: an empty list is a deliberate "no tags", and an absent field is left to the server. */
    @SerialName("tags") val tags: List<Int>? = null,
)

/** `PUT request/{id}`: only the fields sent change; the seasons list is a show's whole new set. */
@Serializable
data class SeerrEditRequestBody(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("seasons") val seasons: List<Int>? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
    @SerialName("tags") val tags: List<Int>? = null,
)

/** A Radarr or Sonarr instance as Seerr lists it; the `active*` fields are what a plain request gets. */
@Serializable
data class SeerrServerDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
    /** What a plain request for an anime series gets, where the instance sets them; Sonarr's list carries these two and no anime tags. */
    @SerialName("activeAnimeProfileId") val activeAnimeProfileId: Int? = null,
    @SerialName("activeAnimeDirectory") val activeAnimeDirectory: String? = null,
)

@Serializable
data class SeerrServerDetailsDto(
    /** The instance itself, as far as its name; every field optional so an older shape still parses. */
    @SerialName("server") val server: SeerrServerRefDto? = null,
    @SerialName("profiles") val profiles: List<SeerrProfileDto> = emptyList(),
    @SerialName("rootFolders") val rootFolders: List<SeerrRootFolderDto> = emptyList(),
    @SerialName("tags") val tags: List<SeerrTagDto> = emptyList(),
)

@Serializable
data class SeerrServerRefDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
    /** The tags a plain request to this instance gets; the details read carries them for both Radarr and Sonarr. */
    @SerialName("activeTags") val activeTags: List<Int> = emptyList(),
    /** Sonarr's own: an anime series gets these instead of the regular ones, and its tags are these alone, not the regular tags. */
    @SerialName("activeAnimeProfileId") val activeAnimeProfileId: Int? = null,
    @SerialName("activeAnimeDirectory") val activeAnimeDirectory: String? = null,
    @SerialName("activeAnimeTags") val activeAnimeTags: List<Int> = emptyList(),
)

@Serializable
data class SeerrProfileDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
)

@Serializable
data class SeerrRootFolderDto(
    @SerialName("id") val id: Int,
    @SerialName("path") val path: String,
    /** Bytes, as the instance reports them; absent from an older shape or a folder the instance could not measure. */
    @SerialName("freeSpace") val freeSpace: Long? = null,
    @SerialName("totalSpace") val totalSpace: Long? = null,
)

private val lenientJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

/**
 * The answer to `POST request`. Only [id] is read when the answer arrives, so nothing in the rest of the body can fail a call
 * the server has already acted on; [toRequest] reads the rest, and gives up quietly where it is not what it expected.
 */
@Serializable(with = SeerrRequestResultSerializer::class)
data class SeerrRequestResultDto(
    val id: Int? = null,
    val answer: JsonObject? = null,
) {
    /** The request the server made, when its answer carried enough to show it as a row; null for a bare `{"id": n}` or anything unexpected. */
    fun toRequest(): SeerrRequestDto? {
        val body = answer ?: return null
        return try {
            lenientJson.decodeFromJsonElement(serializer<SeerrRequestDto>(), body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

internal object SeerrRequestResultSerializer : KSerializer<SeerrRequestResultDto> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SeerrRequestResult")

    override fun deserialize(decoder: Decoder): SeerrRequestResultDto {
        val body = (decoder as JsonDecoder).decodeJsonElement() as? JsonObject ?: return SeerrRequestResultDto()
        return SeerrRequestResultDto((body["id"] as? JsonPrimitive)?.intOrNull, body)
    }

    override fun serialize(
        encoder: Encoder,
        value: SeerrRequestResultDto,
    ) {
        val body = value.answer ?: buildJsonObject { value.id?.let { put("id", JsonPrimitive(it)) } }
        (encoder as JsonEncoder).encodeJsonElement(body)
    }
}
