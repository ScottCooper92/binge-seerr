package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.homelab.core.foundation.ServerVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.days

/** What a launch remembers of the server's profile: the record's own layout and age, and when it is written again. */
class RememberedProfileTest {
    private val saved = SeerrCredentials("https://seerr.example.com/", SeerrAuth.ApiKey("k3y"), SeerrVariant.Jellyseerr)
    private val profile =
        SeerrServerProfile(
            variant = SeerrVariant.Jellyseerr,
            version = ServerVersion.of(2, 7, 3),
            commitTag = "abc",
            updateAvailable = true,
            commitsBehind = 4,
            settings = SeerrPublicSettings(applicationTitle = "Front Room", movie4kEnabled = true, region = "GB"),
        )

    private var now = 1_000_000L
    private val store = KeptProfileStore()
    private val remembered = RememberedProfile(store, { now })

    @Test
    fun `a profile reads back as it was written`() {
        val record = StoredProfile.of(profile, now)

        assertEquals(profile, StoredProfile.decode(record.encode())?.toProfile(now))
    }

    @Test
    fun `a development build with no version reads back with none`() {
        val dev = profile.copy(commitTag = "develop-1a2b")

        assertEquals(dev, StoredProfile.decode(StoredProfile.of(dev, now).encode())?.toProfile(now))
    }

    @Test
    fun `a record from another layout reads as nothing`() {
        val other = StoredProfile.of(profile, now).copy(schema = StoredProfile.SCHEMA + 1)

        assertNull(StoredProfile.decode(other.encode())?.toProfile(now))
    }

    @Test
    fun `a record older than thirty days reads as nothing`() {
        val record = StoredProfile.of(profile, now)

        assertNotNull(record.toProfile(now + 29.days.inWholeMilliseconds))
        assertNull(record.toProfile(now + 31.days.inWholeMilliseconds))
    }

    @Test
    fun `a lineage this version does not know reads as nothing`() {
        assertNull(StoredProfile.of(profile, now).copy(variant = "Plexarr").toProfile(now))
    }

    @Test
    fun `text that is not a record reads as no record`() {
        assertNull(StoredProfile.decode("not json"))
        assertNull(StoredProfile.decode(null))
    }

    @Test
    fun `a field a later version added is ignored`() {
        val raw = StoredProfile.of(profile, now).encode().removeSuffix("}") + ""","extra":1}"""

        assertEquals(profile, StoredProfile.decode(raw)?.toProfile(now))
    }

    @Test
    fun `nothing is remembered until the store is read`() =
        runTest {
            store.kept.value = StoredProfile.of(profile, now)

            assertNull(remembered.orNull())

            remembered.load(saved)

            assertEquals(profile, remembered.orNull())
        }

    @Test
    fun `no saved connection forgets what was held`() =
        runTest {
            store.kept.value = StoredProfile.of(profile, now)
            remembered.load(saved)

            remembered.load(null)

            assertNull(remembered.orNull())
        }

    @Test
    fun `a fresh read is kept, and the same one is not written again the same day`() =
        runTest {
            remembered.remember(saved, profile)
            remembered.remember(saved, profile)

            assertEquals(1, store.writes)
            assertEquals(profile, remembered.orNull())
        }

    @Test
    fun `an unchanged profile is written again after a day so it does not expire`() =
        runTest {
            remembered.remember(saved, profile)
            now += 2.days.inWholeMilliseconds

            remembered.remember(saved, profile)

            assertEquals(2, store.writes)
        }

    @Test
    fun `a changed profile replaces the one kept`() =
        runTest {
            remembered.remember(saved, profile)
            val upgraded = profile.copy(version = ServerVersion.of(3, 0, 0))

            remembered.remember(saved, upgraded)

            assertEquals(2, store.writes)
            assertEquals(upgraded, remembered.orNull())
        }

    @Test
    fun `forgetting drops what is held, so the next read is written again`() =
        runTest {
            remembered.remember(saved, profile)
            remembered.forget()

            assertNull(remembered.orNull())
            remembered.remember(saved, profile)

            assertEquals(2, store.writes)
        }

    /** A store that only keeps a profile, so what is pinned is what [RememberedProfile] asks of it. */
    private class KeptProfileStore : CredentialStore {
        val kept = MutableStateFlow<StoredProfile?>(null)
        var writes = 0

        override val credentials: Flow<SeerrCredentials?> = flowOf(null)
        override val signedOutServer: Flow<String?> = flowOf(null)
        override val profile: Flow<StoredProfile?> get() = kept

        override suspend fun saveProfile(
            credentials: SeerrCredentials,
            profile: StoredProfile,
        ) {
            writes++
            kept.value = profile
        }

        override suspend fun save(credentials: SeerrCredentials) = true

        override suspend fun clear() = Unit

        override suspend fun signOut() = Unit
    }
}
