package io.github.scottcooper92.binge.seerr.ui.users.settings

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.ui.users.toUserOrigin
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
        @Assisted private val userId: Int,
    ) : EditorViewModel<GeneralSettings>() {
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
                settings.toGeneralSettings(
                    canEditQuotas = permissions.canManageUsers,
                    canEditEmail = permissions.canManageUsers || target.await().userType.toUserOrigin() == UserOrigin.Local,
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

internal fun SeerrUserMainSettingsDto.toGeneralSettings(
    canEditQuotas: Boolean,
    canEditEmail: Boolean,
): GeneralSettings =
    GeneralSettings(
        displayName = username.orEmpty(),
        email = email.orEmpty(),
        discordId = discordId.orEmpty(),
        locale = locale.orEmpty(),
        region = region.orEmpty(),
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

/** A blank quota field is sent as null, which the server reads as "use the default". */
internal fun GeneralSettings.toDto(): SeerrUserMainSettingsDto =
    SeerrUserMainSettingsDto(
        username = displayName.trim(),
        email = email.trim().takeIf { it.isNotEmpty() },
        discordId = discordId.trim(),
        locale = locale.trim(),
        region = region.trim(),
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
