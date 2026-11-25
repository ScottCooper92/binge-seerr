package com.cooper.binge.companion.seerr.auth

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.homelab.core.foundation.ServerVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days

/**
 * The last complete [SeerrServerProfile] read for the saved server, kept beside its login so a cold launch knows the server's
 * lineage before the first call. It shapes a first frame and nothing else: [SeerrConnection.profile] still reads the server.
 * [schema] says which layout wrote it, so a layout that changed reads as nothing, and [savedAt] is when, in epoch milliseconds.
 */
@Serializable
data class StoredProfile(
    val schema: Int = SCHEMA,
    val savedAt: Long,
    val variant: String,
    val version: String? = null,
    val commitTag: String? = null,
    val updateAvailable: Boolean = false,
    val commitsBehind: Int = 0,
    val settings: SeerrPublicSettings = SeerrPublicSettings(),
) {
    /** The profile this record holds, or null for one an older or newer layout wrote, or one older than [MAX_AGE]. */
    fun toProfile(now: Long): SeerrServerProfile? {
        if (schema != SCHEMA || now - savedAt > MAX_AGE_MILLIS) return null
        val lineage = SeerrVariant.entries.firstOrNull { it.name == variant } ?: return null
        return SeerrServerProfile(lineage, ServerVersion.parse(version), commitTag, updateAvailable, commitsBehind, settings)
    }

    fun encode(): String = JSON.encodeToString(this)

    companion object {
        const val SCHEMA = 1
        private val MAX_AGE_MILLIS = 30.days.inWholeMilliseconds
        private val JSON = Json { ignoreUnknownKeys = true }

        fun of(
            profile: SeerrServerProfile,
            now: Long,
        ) = StoredProfile(
            savedAt = now,
            variant = profile.variant.name,
            version = profile.versionLabel,
            commitTag = profile.commitTag,
            updateAvailable = profile.updateAvailable,
            commitsBehind = profile.commitsBehind,
            settings = profile.settings,
        )

        /** A record from [raw], or null when it is not one: a stored value is never worth failing a read for. */
        fun decode(raw: String?): StoredProfile? = raw?.let { attempt { JSON.decodeFromString<StoredProfile>(it) }.getOrNull() }
    }
}
