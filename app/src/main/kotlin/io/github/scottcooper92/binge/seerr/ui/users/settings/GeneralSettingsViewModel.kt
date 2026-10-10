package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import io.github.scottcooper92.binge.seerr.seerr.SeerrPublicSettings
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.isAdminBitmask
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.ui.settings.server.ListChoicesLoader
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerList
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerListCatalog
import io.github.scottcooper92.binge.seerr.ui.users.OWNER_USER_ID
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.ui.users.toUserOrigin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
        @ApplicationScope appScope: CoroutineScope,
        @Assisted private val userId: Int,
    ) : ExtrasEditorViewModel<GeneralSettings, UserGeneralExtras>(UserGeneralExtras(), dispatcher) {
        /**
         * A user's General saves as it changes (#930). Its endpoint assigns every field it is sent, so each write carries the
         * whole record as saved plus the change, never the change alone.
         */
        override val saveAsMadeScope: CoroutineScope = appScope

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
                val viewerUser = viewer.await()
                val permissions = viewerUser.toPermissions()
                val user = target.await()
                val server = profile.await()
                val variant = server.variant
                val defaults = public.await().toDiscoverDefaults()
                editExtras { it.copy(variant = variant, serverDefaults = defaults) }
                settings
                    .toGeneralSettings(
                        // The server assigns the quota fields only when the target is neither the viewer nor a manager, and
                        // silently keeps the old values otherwise, so the editor is offered under the same rule.
                        canEditQuotas = permissions.canManageUsers && viewerUser?.id != userId && !user.toPermissions().canManageUsers,
                        canEditEmail = permissions.canManageUsers || user.userType.toUserOrigin() == UserOrigin.Local,
                        fallbackName = user.fallbackName(),
                        // The profile's capability, not the lineage: Jellyseerr before 2.2 has one region too (#1012).
                        streamingRegions = server.hasStreamingRegion,
                    ).withAccount(user)
            }

        override suspend fun write(draft: GeneralSettings): GeneralSettings {
            connection.api().updateUserMainSettings(userId, draft.toDto())
            return load()
        }

        override fun canSave(draft: GeneralSettings): Boolean = draft.valid

        private val lists =
            ListChoicesLoader(
                scope = viewModelScope,
                dispatcher = dispatcher,
                catalog = listCatalog,
                held = { currentExtras().lists[it] },
                set = { kind, choices -> editExtras { it.copy(lists = it.lists + (kind to choices)) } },
            )

        /** Reads [kind]'s list for its picker, once; a failed read can be asked for again. */
        fun loadList(kind: ServerList) = lists.load(kind)

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

internal fun SeerrUserDto.role(): UserRole =
    when {
        id == OWNER_USER_ID -> UserRole.Owner
        isAdminBitmask(permissions ?: 0) -> UserRole.Admin
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
        emailRequired = user.id == OWNER_USER_ID || origin !in MEDIA_SERVER_ORIGINS,
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
