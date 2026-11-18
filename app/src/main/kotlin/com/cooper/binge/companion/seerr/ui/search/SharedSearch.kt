package com.cooper.binge.companion.seerr.ui.search

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.flags.SearchToggles
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.foundation.TextKey
import com.cooper.homelab.core.foundation.UiText
import com.cooper.homelab.core.presentation.search.RecentSearches
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.presentation.search.SettingsSearchViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.cooper.homelab.core.presentation.search.SearchAccess as SharedSearchAccess

/**
 * What a shared search entry opens, in this app's terms: where it goes ([target]), the glyph the page draws beside it ([icon]), and the
 * gate on the connected server that decides whether it is offered ([shown], with the target's own gate applied besides).
 */
internal class SeerrSearchTarget(
    val target: SearchTarget,
    val icon: ImageVector,
    val shown: (SearchAccess) -> Boolean,
)

/** A string resource of this app named for the shared library, which looks every text up by its resource name. */
private class ResourceTextKey(
    override val id: String,
) : TextKey

private fun Resources.text(
    @StringRes id: Int,
): UiText = UiText.keyed(ResourceTextKey(getResourceEntryName(id)))

/** The app's index, as the shared search's entries: the same ids, labels and gates, with each text named by its resource. */
internal fun sharedSettingsIndex(resources: Resources): List<SettingEntry<SeerrSearchTarget>> =
    settingsIndex.map { entry ->
        SettingEntry(
            id = entry.id,
            label = resources.text(entry.label),
            section = resources.text(entry.section),
            target = SeerrSearchTarget(entry.target, entry.icon, entry.shown),
            pageLabel = entry.pageLabel?.let(resources::text),
            keywords = entry.keywords?.let(resources::text),
            row = entry.row,
        )
    }

/**
 * The shared settings search for the page: its view model over this app's index, made once and kept across a rotation. What it
 * reads is the signed-in user, from the connection's own copy so a user read up to a minute old is reused, and the server's profile;
 * nothing is fetched that the hub has not already read. The viewer the entries were offered to travels with the page's state, which is
 * what a result's route is built from.
 */
@HiltViewModel
internal class SettingsSearchHost
    @Inject
    constructor(
        @ApplicationContext context: Context,
        connection: SeerrConnection,
        recents: RecentSearches,
        flags: FeatureFlags,
        @ApplicationScope appScope: CoroutineScope,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /** Whether a result for an on/off setting carries its switch. */
        val searchTogglesEnabled: StateFlow<Boolean> =
            flags.flags
                .map { it[SearchToggles] }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, flags.flags.value[SearchToggles])

        val entries: List<SettingEntry<SeerrSearchTarget>> = sharedSettingsIndex(context.resources)

        val model =
            SettingsSearchViewModel(
                scope = viewModelScope,
                appScope = appScope,
                dispatcher = dispatcher,
                entries = entries,
                loadAvailability = {
                    val user = connection.recentAuthenticatedUser()
                    val access = SearchAccess(user.id, user.toPermissions().isAdmin, connection.profile())
                    SharedSearchAccess({ entry -> entry.target.available(access) }, viewer = access)
                },
                recents = recents,
                classify = { it.toServerFailure() },
            )
    }

/** Whether the search may offer [this]: its page is one the viewer can open, and its own gate passes. */
internal fun SeerrSearchTarget.available(access: SearchAccess): Boolean = target.opensFor(access) && shown(access)
