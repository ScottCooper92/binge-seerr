package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrPublicSettings
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.ui.settings.server.ListChoices
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerList
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerListCatalog
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.ui.users.toUserOrigin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The general page: how the account signs in and its role, the display name and email, the display language and
 * Discover filters, and, for a manager, the user's request quotas. The server's own read after a write is what is
 * adopted, since it answers with the quotas re-applied against its defaults. Beside the form it keeps the server's own
 * Discover settings, which a blank user setting falls back to, and the region and language lists, each read only when
 * its picker opens.
 */
@HiltViewModel(assistedFactory = GeneralSettingsViewModel.Factory::class)
class GeneralSettingsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val listCatalog: ServerListCatalog,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val userId: Int,
    ) : ExtrasEditorViewModel<GeneralSettings, UserGeneralExtras>(UserGeneralExtras(), dispatcher) {
        init {
            reload()
        }

        override suspend fun load(): GeneralSettings =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val target = async { api.user(userId) }
                val profile = async { connection.profile() }
                // The defaults only label the blank choices, so a server that can't send them still loads the page.
                val public = async { runCatching { api.publicSettings() }.getOrNull() }
                val settings = api.userMainSettings(userId)
                val permissions = viewer.await().toPermissions()
                val user = target.await()
                val variant = profile.await().variant
                val defaults = public.await().toDiscoverDefaults()
                editExtras { it.copy(variant = variant, serverDefaults = defaults) }
                settings
                    .toGeneralSettings(
                        canEditQuotas = permissions.canManageUsers,
                        canEditEmail = permissions.canManageUsers || user.userType.toUserOrigin() == UserOrigin.Local,
                        fallbackName = user.fallbackName(),
                        // Only Overseerr lacks the streaming region; an unrecognised server is read as the newer lineage.
                        streamingRegions = variant != SeerrVariant.Overseerr,
                    ).withAccount(user)
            }

        override suspend fun write(draft: GeneralSettings): GeneralSettings {
            connection.api().updateUserMainSettings(userId, draft.toDto())
            return load()
        }

        override fun canSave(draft: GeneralSettings): Boolean = draft.valid

        /** Reads [kind]'s list for its picker, once; a failed read can be asked for again. */
        fun loadList(kind: ServerList) {
            val held = currentExtras().lists[kind]
            if (held is ListChoices.Ready || held == ListChoices.Loading) return
            editExtras { it.copy(lists = it.lists + (kind to ListChoices.Loading)) }
            viewModelScope.launch(dispatcher) {
                val choices = runCatching { listCatalog.entries(kind) }.fold({ ListChoices.Ready(it) }, { ListChoices.Failed })
                editExtras { it.copy(lists = it.lists + (kind to choices)) }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): GeneralSettingsViewModel
        }
    }

private fun SeerrPublicSettings?.toDiscoverDefaults(): ServerDiscoverDefaults =
    ServerDiscoverDefaults(
        locale = this?.locale.orEmpty(),
        region = (this?.discoverRegion ?: this?.region).orEmpty(),
        streamingRegion = this?.streamingRegion.orEmpty(),
        originalLanguage = this?.originalLanguage.orEmpty(),
    )

/** The owner is the server's first account, as the web client reads it. */
private const val OWNER_ID = 1

internal fun SeerrUserDto.role(): UserRole =
    when {
        id == OWNER_ID -> UserRole.Owner
        ManageablePermission.Admin in ManageablePermission.decode(permissions ?: 0) -> UserRole.Admin
        else -> UserRole.User
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
    streamingRegions: Boolean = true,
): GeneralSettings =
    GeneralSettings(
        displayName = username.orEmpty(),
        fallbackName = fallbackName,
        email = email.orEmpty(),
        loadedEmail = email.orEmpty(),
        discordId = discordId.orEmpty(),
        locale = locale.orEmpty(),
        region = (region ?: discoverRegion).orEmpty(),
        streamingRegion = streamingRegion.orEmpty().takeIf { streamingRegions },
        originalLanguage = originalLanguage.orEmpty(),
        movieQuotaOverride = movieQuotaLimit != null && movieQuotaDays != null,
        tvQuotaOverride = tvQuotaLimit != null && tvQuotaDays != null,
        movieQuotaLimit = movieQuotaLimit ?: globalMovieQuotaLimit ?: 0,
        movieQuotaDays = movieQuotaDays ?: globalMovieQuotaDays ?: DEFAULT_QUOTA_DAYS,
        tvQuotaLimit = tvQuotaLimit ?: globalTvQuotaLimit ?: 0,
        tvQuotaDays = tvQuotaDays ?: globalTvQuotaDays ?: DEFAULT_QUOTA_DAYS,
        watchlistSyncMovies = watchlistSyncMovies,
        watchlistSyncTv = watchlistSyncTv,
        defaultMovieQuota = quotaDefault(globalMovieQuotaLimit, globalMovieQuotaDays),
        defaultTvQuota = quotaDefault(globalTvQuotaLimit, globalTvQuotaDays),
        canEditQuotas = canEditQuotas,
        canEditEmail = canEditEmail,
    )

/** How [user] signs in, their role, and whether the web client would require their email. */
internal fun GeneralSettings.withAccount(user: SeerrUserDto): GeneralSettings {
    val origin = user.userType.toUserOrigin()
    return copy(
        accountType = origin,
        role = user.role(),
        emailRequired = user.id == OWNER_ID || origin !in MEDIA_SERVER_ORIGINS,
    )
}

/** The accounts the web client lets go without an email, unless they are the owner's. */
private val MEDIA_SERVER_ORIGINS = setOf(UserOrigin.Jellyfin, UserOrigin.Emby)

/**
 * A quota whose override is off is sent as null, which the server reads as "use the default". The one region field
 * goes out under both lineages' names. The Discord ID this page does not show is sent back as it was read: the server
 * assigns every key from the body, so one it does not find is cleared.
 */
internal fun GeneralSettings.toDto(): SeerrUserMainSettingsDto =
    SeerrUserMainSettingsDto(
        username = displayName.trim(),
        email = email.trim().takeIf { it.isNotEmpty() },
        discordId = discordId.trim(),
        locale = locale.trim(),
        region = region.trim(),
        discoverRegion = region.trim(),
        streamingRegion = streamingRegion?.trim(),
        originalLanguage = originalLanguage.trim(),
        movieQuotaLimit = movieQuotaLimit.takeIf { movieQuotaOverride },
        movieQuotaDays = movieQuotaDays.takeIf { movieQuotaOverride },
        tvQuotaLimit = tvQuotaLimit.takeIf { tvQuotaOverride },
        tvQuotaDays = tvQuotaDays.takeIf { tvQuotaOverride },
        watchlistSyncMovies = watchlistSyncMovies,
        watchlistSyncTv = watchlistSyncTv,
    )

private fun quotaDefault(
    limit: Int?,
    days: Int?,
): QuotaDefault? = if (limit != null && days != null) QuotaDefault(limit, days) else null
