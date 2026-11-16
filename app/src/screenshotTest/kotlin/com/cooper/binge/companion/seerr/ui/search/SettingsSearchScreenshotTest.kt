package com.cooper.binge.companion.seerr.ui.search

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage

private val admin = SearchAccess(userId = 1, isAdmin = true, profile = SeerrServerProfile(SeerrVariant.Seerr, version = null))

/** The settings search: the empty page, results for an admin, results for a viewer who is not one, and a query nothing matches. */
class SettingsSearchScreenshotTest {
    /** The page as it opens: the field, and a line saying what to type. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun prompt() = SettingsSearchScreen(SettingsSearchUiState.Ready(admin), onRetry = {}, open = {}, onBack = {})

    /** The page as it opens once something has been opened from it: the recents lead, with Clear. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun recent() =
        SettingsSearchScreen(
            SettingsSearchUiState.Ready(
                admin,
                recent =
                    listOf(
                        settingsIndex.single { it.target == SearchTarget.Server(ServerSettingsPage.Logs) },
                        settingsIndex.single { it.target == SearchTarget.Account(UserSettingsPage.Password) },
                        settingsIndex.single { it.target == SearchTarget.ThisApp },
                    ),
            ),
            onRetry = {},
            open = {},
            onBack = {},
        )

    /** A query that finds pages on both sides of the server's settings and the viewer's own. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun results() = SettingsSearchScreen(SettingsSearchUiState.Ready(admin), onRetry = {}, open = {}, onBack = {}, initialQuery = "notif")

    /** A query that finds settings: each result says which page it is on. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun settingResults() =
        SettingsSearchScreen(SettingsSearchUiState.Ready(admin), onRetry = {}, open = {}, onBack = {}, initialQuery = "region")

    /** A viewer who is not an admin finds only what they may open. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun resultsRestricted() =
        SettingsSearchScreen(
            SettingsSearchUiState.Ready(admin.copy(userId = 7, isAdmin = false)),
            onRetry = {},
            open = {},
            onBack = {},
            initialQuery = "notif",
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun noMatch() = SettingsSearchScreen(SettingsSearchUiState.Ready(admin), onRetry = {}, open = {}, onBack = {}, initialQuery = "zzzz")

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun error() = SettingsSearchScreen(SettingsSearchUiState.Error(SeerrError.Unreachable), onRetry = {}, open = {}, onBack = {})
}
