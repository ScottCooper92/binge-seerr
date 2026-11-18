package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.ItemGroup
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.homelab.core.presentation.tip.TipState

private val SampleApp =
    AppSettings(
        bugReportUrl = "https://github.com/ScottCooper92/app-feedback/issues/new?template=seerr-bug.yml",
        shakeToReport = true,
        binge = BingeStatus.Connected,
    )

private fun notifications() =
    NotificationSettings(
        offered = NotificationSignal.entries,
        enabled = setOf(NotificationSignal.PendingRequests, NotificationSignal.RequestAvailable),
        blocked = false,
        lastRunMillis = null,
        nextRunMillis = null,
    )

/** This app's own page: Binge, bug reporting, what it may report on its own, the tip jar, its notifications, and About. */
class ThisAppScreenshotTest {
    /** Binge connected and the tip jar priced, which is the page at its fullest. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun ready() =
        ThisAppFrame(
            ThisAppUiState.Ready(SampleApp, notifications()),
            tip =
                TipState.Ready(
                    mapOf(
                        3 to "£3",
                        5 to "£5",
                        10 to "£10",
                        20 to "£20",
                    ),
                ),
        )

    /** Without Play, or before the products are live, there is no tip row. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withoutTipJar() = ThisAppFrame(ThisAppUiState.Ready(SampleApp.copy(binge = BingeStatus.NotInstalled)))

    /** The system would show nothing, so the row that says so sits between the toggles and the schedule. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notificationsBlocked() =
        ItemGroup(
            title = stringResource(R.string.settings_group_notify_me),
            rows = notificationRows(notifications().copy(blocked = true), { _, _ -> }, {}, onOpenInterval = {}, now = 0L),
        )

    /** Android holds the poll back and the interval is long: the interval row, then the row that says why checks run late. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notificationsRestricted() =
        ItemGroup(
            title = stringResource(R.string.settings_group_notify_me),
            rows =
                notificationRows(
                    notifications().copy(unrestricted = false, intervalMinutes = 120),
                    { _, _ -> },
                    {},
                    onOpenInterval = {},
                    now = 0L,
                ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notificationsOff() =
        ItemGroup(
            title = stringResource(R.string.settings_group_notify_me),
            rows = notificationRows(notifications().copy(enabled = emptySet()), { _, _ -> }, {}, onOpenInterval = {}, now = 0L),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = ThisAppFrame(ThisAppUiState.Loading)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun fontScales() = ThisAppFrame(ThisAppUiState.Ready(SampleApp, notifications()))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = ThisAppFrame(ThisAppUiState.Ready(SampleApp, notifications()))
}

@Composable
private fun ThisAppFrame(
    state: ThisAppUiState,
    tip: TipState = TipState.Unavailable,
) = ThisAppScreen(
    state = state,
    actions =
        ThisAppActions(
            onBack = {},
            onToggleShakeToReport = {},
            onToggleShareUsageData = {},
            onToggleSendCrashReports = {},
        ),
    tip = tip,
)
