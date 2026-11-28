package com.cooper.binge.companion.seerr.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.presentation.search.SettingsSearchState
import com.cooper.homelab.core.uiandroid.search.SettingsSearchScreen

private val admin = SearchAccess(userId = 1, isAdmin = true, profile = SeerrServerProfile(SeerrVariant.Seerr))

/** The shared search page over this app's index, drawn as [access] sees it with [recent] leading when there is one. */
@Composable
private fun Search(
    access: SearchAccess = admin,
    recent: (List<SettingEntry<SeerrSearchTarget>>) -> List<SettingEntry<SeerrSearchTarget>> = { emptyList() },
    initialQuery: String = "",
    state: ((List<SettingEntry<SeerrSearchTarget>>) -> SettingsSearchState<SeerrSearchTarget, SearchAccess>)? = null,
) {
    val entries = sharedSettingsIndex(LocalContext.current.resources)
    SettingsSearchScreen(
        state = state?.invoke(entries) ?: SettingsSearchState.Ready({ it.target.available(access) }, recent(entries), access),
        entries = entries,
        icon = { it.target.icon },
        onOpen = { _, _ -> },
        onBack = {},
        onRetry = {},
        onClearRecent = {},
        initialQuery = initialQuery,
    )
}

private fun List<SettingEntry<SeerrSearchTarget>>.of(target: SearchTarget) = single { it.target.target == target && it.row == null }

/** The settings search: the empty page, results for an admin, results for a viewer who is not one, and a query nothing matches. */
class SettingsSearchScreenshotTest {
    /** The page as it opens: the field, and a line saying what to type. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun prompt() = Search()

    /** The page as it opens once something has been opened from it: the recents lead, with Clear. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun recent() =
        Search(
            recent = {
                listOf(
                    it.of(SearchTarget.Server(ServerSettingsPage.Logs)),
                    it.of(SearchTarget.Account(UserSettingsPage.Password)),
                    it.of(SearchTarget.ThisApp),
                )
            },
        )

    /** A query that finds pages on both sides of the server's settings and the viewer's own. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun results() = Search(initialQuery = "notif")

    /** A query that finds settings: each result says which page it is on. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun settingResults() = Search(initialQuery = "region")

    /** A viewer who is not an admin finds only what they may open. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun resultsRestricted() = Search(access = admin.copy(userId = 7, isAdmin = false), initialQuery = "notif")

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun noMatch() = Search(initialQuery = "zzzz")

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun error() = Search(state = { SettingsSearchState.Failed(ServerFailure.ServerError) })
}
