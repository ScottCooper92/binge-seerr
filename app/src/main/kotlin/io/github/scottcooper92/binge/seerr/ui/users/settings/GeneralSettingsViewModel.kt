package io.github.scottcooper92.binge.seerr.ui.users.settings

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.ui.users.toUserOrigin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The general page: the display name, the contact fields, the discovery locale and, for a
 * manager, the user's request quotas. The server's own read after a write is what is adopted,
 * since it answers with the quotas re-applied against its defaults.
 */
@HiltViewModel(assistedFactory = GeneralSettingsViewModel.Factory::class)
class GeneralSettingsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @Assisted private val userId: Int,
    ) : EditorViewModel<GeneralSettings>(dispatcher) {
        init {
            reload()
        }

        override suspend fun load(): GeneralSettings =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val target = async { api.user(userId) }
                val settings = api.userMainSettings(userId)
                val permissions = viewer.await().toPermissions()
                val user = target.await()
                settings.toGeneralSettings(
                    canEditQuotas = permissions.canManageUsers,
                    canEditEmail = permissions.canManageUsers || user.userType.toUserOrigin() == UserOrigin.Local,
                    fallbackName = user.fallbackName(),
                )
            }

        override suspend fun write(draft: GeneralSettings): GeneralSettings {
            connection.api().updateUserMainSettings(userId, draft.toDto())
            return load()
        }

        override fun canSave(draft: GeneralSettings): Boolean = draft.quotasValid

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): GeneralSettingsViewModel
        }
    }

/**
 * What Seerr shows a user as once they have no display name: their media-server username, else their
 * email. Deliberately not [SeerrUserDto.displayName], which is the stored name where there is one —
 * the placeholder has to answer "or what?" for a field the user is in the middle of clearing.
 */
internal fun SeerrUserDto.fallbackName(): String =
    listOfNotNull(plexUsername, jellyfinUsername, email).firstOrNull { it.isNotBlank() }.orEmpty()

internal fun SeerrUserMainSettingsDto.toGeneralSettings(
    canEditQuotas: Boolean,
    canEditEmail: Boolean,
    fallbackName: String = "",
): GeneralSettings =
    GeneralSettings(
        displayName = username.orEmpty(),
        fallbackName = fallbackName,
        email = email.orEmpty(),
        discordId = discordId.orEmpty(),
        locale = locale.orEmpty(),
        region = (region ?: discoverRegion).orEmpty(),
        streamingRegion = streamingRegion.orEmpty(),
        originalLanguage = originalLanguage.orEmpty(),
        movieQuotaLimit = movieQuotaLimit?.toString().orEmpty(),
        movieQuotaDays = movieQuotaDays?.toString().orEmpty(),
        tvQuotaLimit = tvQuotaLimit?.toString().orEmpty(),
        tvQuotaDays = tvQuotaDays?.toString().orEmpty(),
        watchlistSyncMovies = watchlistSyncMovies,
        watchlistSyncTv = watchlistSyncTv,
        defaultMovieQuota = quotaDefault(globalMovieQuotaLimit, globalMovieQuotaDays),
        defaultTvQuota = quotaDefault(globalTvQuotaLimit, globalTvQuotaDays),
        canEditQuotas = canEditQuotas,
        canEditEmail = canEditEmail,
    )

/**
 * A blank quota field is sent as null, which the server reads as "use the default". The one region
 * field goes out under both lineages' names, and the streaming region this page does not show is
 * sent back as it was read: the server assigns every key from the body, so one it does not find is
 * cleared.
 */
internal fun GeneralSettings.toDto(): SeerrUserMainSettingsDto =
    SeerrUserMainSettingsDto(
        username = displayName.trim(),
        email = email.trim().takeIf { it.isNotEmpty() },
        discordId = discordId.trim(),
        locale = locale.trim(),
        region = region.trim(),
        discoverRegion = region.trim(),
        streamingRegion = streamingRegion.trim(),
        originalLanguage = originalLanguage.trim(),
        movieQuotaLimit = movieQuotaLimit.trim().toIntOrNull(),
        movieQuotaDays = movieQuotaDays.trim().toIntOrNull(),
        tvQuotaLimit = tvQuotaLimit.trim().toIntOrNull(),
        tvQuotaDays = tvQuotaDays.trim().toIntOrNull(),
        watchlistSyncMovies = watchlistSyncMovies,
        watchlistSyncTv = watchlistSyncTv,
    )

private fun quotaDefault(
    limit: Int?,
    days: Int?,
): QuotaDefault? = if (limit != null && days != null) QuotaDefault(limit, days) else null
