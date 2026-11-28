package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

/**
 * A Radarr or Sonarr instance as the admin configured it (`GET settings/radarr`, `settings/sonarr`),
 * and the body its `POST` and `PUT` take — the server stores what it is sent, so the record is the
 * body. Radarr's own field is [minimumAvailability]; Sonarr's are the series types, the anime
 * destination, the season folders and the language profile (Sonarr 3 only). Every field defaults so
 * a Sonarr record parses as a Radarr one where the reader does not care.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrServiceSettingsDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("apiKey") val apiKey: String? = null,
    @EncodeDefault @SerialName("useSsl") val useSsl: Boolean = false,
    @SerialName("baseUrl") val baseUrl: String? = null,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeProfileName") val activeProfileName: String? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
    @EncodeDefault @SerialName("tags") val tags: List<Int> = emptyList(),
    @EncodeDefault @SerialName("is4k") val is4k: Boolean = false,
    @EncodeDefault @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("externalUrl") val externalUrl: String? = null,
    @EncodeDefault @SerialName("syncEnabled") val syncEnabled: Boolean = false,
    @EncodeDefault @SerialName("preventSearch") val preventSearch: Boolean = false,
    @EncodeDefault @SerialName("tagRequests") val tagRequests: Boolean = false,
    @SerialName("minimumAvailability") val minimumAvailability: String? = null,
    @SerialName("seriesType") val seriesType: String? = null,
    @SerialName("animeSeriesType") val animeSeriesType: String? = null,
    @SerialName("activeAnimeProfileId") val activeAnimeProfileId: Int? = null,
    @SerialName("activeAnimeProfileName") val activeAnimeProfileName: String? = null,
    @SerialName("activeAnimeDirectory") val activeAnimeDirectory: String? = null,
    @SerialName("animeTags") val animeTags: List<Int>? = null,
    @SerialName("enableSeasonFolders") val enableSeasonFolders: Boolean? = null,
    @SerialName("activeLanguageProfileId") val activeLanguageProfileId: Int? = null,
    @SerialName("activeAnimeLanguageProfileId") val activeAnimeLanguageProfileId: Int? = null,
    @SerialName("monitorNewItems") val monitorNewItems: String? = null,
)

/** `POST settings/{radarr,sonarr}/test`: the address and key to reach the instance with. */
@Serializable
data class SeerrDvrTestBody(
    @SerialName("hostname") val hostname: String,
    @SerialName("port") val port: Int,
    @SerialName("useSsl") val useSsl: Boolean,
    @SerialName("baseUrl") val baseUrl: String? = null,
    @SerialName("apiKey") val apiKey: String,
)

/** What a reached instance offers: its profiles, folders and tags; Sonarr 3 adds language profiles, Sonarr 4 sends null. */
@Serializable
data class SeerrDvrTestResultDto(
    @SerialName("profiles") val profiles: List<SeerrProfileDto> = emptyList(),
    @SerialName("rootFolders") val rootFolders: List<SeerrRootFolderDto> = emptyList(),
    @SerialName("tags") val tags: List<SeerrTagDto> = emptyList(),
    @SerialName("languageProfiles") val languageProfiles: List<SeerrProfileDto>? = null,
    @SerialName("urlBase") val urlBase: String? = null,
)

/**
 * An override rule (`overrideRule`, Jellyseerr 2.2+): the conditions and the overrides for one
 * instance. The list-valued conditions are strings as the server stores them — ids joined with a
 * comma, except [language], whose ISO codes are joined with a pipe; [tags] are tag ids, comma-joined.
 */
@Serializable(with = SeerrOverrideRuleSerializer::class)
data class SeerrOverrideRuleDto(
    val id: Int? = null,
    val radarrServiceId: Int? = null,
    val sonarrServiceId: Int? = null,
    val users: String? = null,
    val genre: String? = null,
    val language: String? = null,
    val keywords: String? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val tags: String? = null,
    /**
     * Set on an update: every condition and override left null is sent as an explicit `null`. `PUT overrideRule/:id` assigns
     * each field from the body, and a key that is absent is not written, so a cleared field would otherwise keep its old value.
     * A create leaves them out.
     */
    val clearsAbsent: Boolean = false,
)

/** The wire shape of [SeerrOverrideRuleDto]: its fields and nothing else, so the encoding can be finished by hand. */
@Serializable
private class OverrideRuleWire(
    @SerialName("id") val id: Int? = null,
    @SerialName("radarrServiceId") val radarrServiceId: Int? = null,
    @SerialName("sonarrServiceId") val sonarrServiceId: Int? = null,
    @SerialName("users") val users: String? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("keywords") val keywords: String? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
    @SerialName("tags") val tags: String? = null,
)

/** The keys `PUT overrideRule/:id` assigns from the body, which an update must always say: a value, or `null` to clear. */
private val OVERRIDE_RULE_CLEARABLE_KEYS =
    listOf("radarrServiceId", "sonarrServiceId", "users", "genre", "language", "keywords", "profileId", "rootFolder", "tags")

/** Encodes [SeerrOverrideRuleDto] as the server reads it, adding the explicit nulls an update needs, which `explicitNulls = false` drops. */
internal object SeerrOverrideRuleSerializer : KSerializer<SeerrOverrideRuleDto> {
    override val descriptor: SerialDescriptor = serializer<OverrideRuleWire>().descriptor

    override fun deserialize(decoder: Decoder): SeerrOverrideRuleDto =
        decoder.decodeSerializableValue(serializer<OverrideRuleWire>()).let {
            SeerrOverrideRuleDto(
                id = it.id,
                radarrServiceId = it.radarrServiceId,
                sonarrServiceId = it.sonarrServiceId,
                users = it.users,
                genre = it.genre,
                language = it.language,
                keywords = it.keywords,
                profileId = it.profileId,
                rootFolder = it.rootFolder,
                tags = it.tags,
            )
        }

    override fun serialize(
        encoder: Encoder,
        value: SeerrOverrideRuleDto,
    ) {
        val wire =
            OverrideRuleWire(
                id = value.id,
                radarrServiceId = value.radarrServiceId,
                sonarrServiceId = value.sonarrServiceId,
                users = value.users,
                genre = value.genre,
                language = value.language,
                keywords = value.keywords,
                profileId = value.profileId,
                rootFolder = value.rootFolder,
                tags = value.tags,
            )
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder == null) {
            encoder.encodeSerializableValue(serializer<OverrideRuleWire>(), wire)
            return
        }
        val fields = jsonEncoder.json.encodeToJsonElement(serializer<OverrideRuleWire>(), wire).jsonObject
        val cleared =
            if (value.clearsAbsent) {
                OVERRIDE_RULE_CLEARABLE_KEYS
                    .filterNot {
                        it in fields
                    }.associateWith { JsonNull }
            } else {
                emptyMap()
            }
        jsonEncoder.encodeJsonElement(JsonObject(fields + cleared))
    }
}

/** The admin's external URL where set, else the instance's own address; a LAN one opens fine from the same network. */
fun SeerrServiceSettingsDto.browsableUrl(): String? {
    externalUrl?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
    val host = hostname?.takeIf { it.isNotBlank() } ?: return null
    val scheme = if (useSsl) "https" else "http"
    val portPart = port?.let { ":$it" }.orEmpty()
    val path =
        baseUrl
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }
            ?.let { "/$it" }
            .orEmpty()
    return "$scheme://$host$portPart$path"
}
