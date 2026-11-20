package com.cooper.binge.companion.seerr.auth

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.days

private val REWRITE_AFTER_MILLIS = 1.days.inWholeMilliseconds

/**
 * The profile [CredentialStore] kept from an earlier launch, held in memory for the saved connection. A screen that wants its
 * first frame to match the server reads [orNull] before any call has answered. It is never the answer to
 * [SeerrConnection.profile], which always asks the server once for each launch: this is a guess to draw from, and a fresh read
 * replaces it through [remember].
 */
internal class RememberedProfile(
    private val store: CredentialStore,
    private val clock: () -> Long,
) {
    private class Held(
        val credentials: SeerrCredentials,
        val profile: SeerrServerProfile,
        val savedAt: Long,
    ) {
        /** Whether the store already keeps [read] for [saved], written recently enough to leave as it is. */
        fun isCurrent(
            saved: SeerrCredentials,
            read: SeerrServerProfile,
            now: Long,
        ): Boolean = credentials == saved && profile == read && now - savedAt < REWRITE_AFTER_MILLIS
    }

    @Volatile
    private var held: Held? = null

    fun orNull(): SeerrServerProfile? = held?.profile

    /** Loads what the store kept for [saved], or drops what is held when nothing is saved. */
    suspend fun load(saved: SeerrCredentials?) {
        if (saved == null) {
            held = null
        } else if (held?.credentials != saved) {
            held = store.profile.first()?.let { record -> record.toProfile(clock())?.let { Held(saved, it, record.savedAt) } }
        }
    }

    /** Keeps [profile], read for [saved], for the next launch. A profile already kept and still fresh is not written again. */
    suspend fun remember(
        saved: SeerrCredentials,
        profile: SeerrServerProfile,
    ) {
        val now = clock()
        if (held?.isCurrent(saved, profile, now) == true) return
        attempt { store.saveProfile(saved, StoredProfile.of(profile, now)) }
        held = Held(saved, profile, now)
    }

    /** Drops what is held, for a connection that is changing or going. The store drops its own copy with the connection. */
    fun forget() {
        held = null
    }
}
