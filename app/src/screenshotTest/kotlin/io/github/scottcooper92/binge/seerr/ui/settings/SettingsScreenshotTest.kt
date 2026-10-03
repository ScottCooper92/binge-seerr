package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.notifications.NotificationSignal
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobInterval
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobOutcome
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerJob
import io.github.scottcooper92.binge.seerr.ui.settings.server.jobRow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The Settings root. Every group is one screen tall together, so the page frames carry the layout and
 * the two shapes the page takes (an admin and a plain user), and the groups are framed on their own
 * for the variants the page frame would not reach. Times are left null: a relative date would move the
 * baseline with the clock.
 */
class SettingsScreenshotTest {
    /** An admin on Seerr 3: every group, with the rows the newest lineage adds. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun adminLayout() = SettingsFrame(adminState())

    /** A signed-in user who may not read the server's settings: the connection, what they get notified of, and the app. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun plainUser() = SettingsFrame(adminState().copy(config = null, connection = connection(SignInKind.Session)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = SettingsFrame(SettingsUiState.Loading)
}

/** The row groups on their own, each in the variants that change what a row says. */
class SettingsGroupsScreenshotTest {
    /** Signed in with an API key, on a server that is current. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun connectionCurrent() = Group(R.string.settings_group_connection, connectionRows(connection(SignInKind.ApiKey), server(), {}, {}))

    /** Signed in as a user, on a server that is commits behind: the version row says so and becomes a link. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun connectionBehind() =
        Group(
            R.string.settings_group_connection,
            connectionRows(connection(SignInKind.Session), server().copy(updateAvailable = true, commitsBehind = 12), {}, {}),
        )

    /** The name has not come back yet. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun connectionNameUnknown() =
        Group(R.string.settings_group_connection, connectionRows(connection(SignInKind.Session).copy(userName = null), server(), {}, {}))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun generalFull() = Group(R.string.settings_group_general, generalRows(general(), {}))

    /** Overseerr 1.x: no sliders, no network page, no metadata page, and the hide-available state unread. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun generalMinimal() =
        Group(
            R.string.settings_group_general,
            generalRows(
                general().copy(discoverSliders = false, network = false, metadata = false, hideAvailable = null, applicationUrl = null),
                {},
            ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun mediaServerJellyfin() =
        Group(
            R.string.server_settings_media_server,
            mediaServerRows(server().copy(mediaServer = SeerrMediaServer.Jellyfin), {
            }),
        )

    /** An instance with an id opens in the app; one with only an address leaves it; one with neither is inert. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun serviceInstances() = Group(R.string.settings_group_services, serviceRows(services(), {}, { _, _ -> }))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun requestPolicyLimited() = Group(R.string.settings_group_requests, requestPolicyRows(policy()))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun requestPolicyUnlimited() =
        Group(
            R.string.settings_group_requests,
            requestPolicyRows(RequestPolicy(SeerrDefaultAccess.NoRequests, movieLimit = null, tvLimit = null)),
        )

    /** One agent on, one off, one whose settings could not be read and so has no row. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun agents() =
        Group(R.string.settings_group_notifications, agentRows(NotificationAgents(emailEnabled = true, discordEnabled = null), {}, {}))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun system() = Group(R.string.settings_group_system, systemRows(systemInfo(), {}))

    /** Each job state at once: running, idle, a run that finished, a run that failed, and a run in flight. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun jobStates() = JobStatesGroup()

    /** The same five rows at 1.5 and 2.0 font scale, where the icon actions must not squeeze the names. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun jobStatesFontScale() = JobStatesGroup()

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun jobStatesSpanish() = JobStatesGroup()

    /** The system would show nothing, so the row that says so sits between the toggles and the schedule. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notificationsBlocked() =
        Group(R.string.settings_group_notify_me, notificationRows(notifications().copy(blocked = true), noActions()))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notificationsOff() =
        Group(R.string.settings_group_notify_me, notificationRows(notifications().copy(enabled = emptySet()), noActions()))
}

@Composable
private fun Group(
    title: Int,
    rows: List<com.binge.designsystem.component.ListItem>,
) = ItemGroup(title = stringResource(title), rows = rows)

private fun connection(kind: SignInKind) = ConnectionSummary(baseUrl = "https://seerr.home.lan", signInKind = kind, userName = "Scott")

private fun server() =
    ServerSummary(
        title = "Seerr",
        variant = SeerrVariant.Seerr,
        versionLabel = "3.4.0",
        updateAvailable = false,
        commitsBehind = 0,
    )

private fun general() =
    GeneralSettings(
        applicationTitle = "Seerr",
        applicationUrl = "https://requests.home.lan",
        displayLanguage = "en",
        hideAvailable = true,
        discoverSliders = true,
        network = true,
        metadata = true,
    )

private fun services() =
    listOf(
        ServerService(
            id = 1,
            name = "Radarr",
            type = ServiceType.Radarr,
            url = "https://radarr.home.lan",
            qualityProfile = "HD-1080p",
            rootFolder = "/movies",
            is4k = false,
            isDefault = true,
        ),
        ServerService(
            id = 2,
            name = "Sonarr 4K",
            type = ServiceType.Sonarr,
            url = "https://sonarr.home.lan",
            qualityProfile = "Ultra-HD",
            rootFolder = "/tv",
            is4k = true,
            isDefault = false,
        ),
        ServerService(
            id = null,
            name = "Legacy Radarr",
            type = ServiceType.Radarr,
            url = "https://old.home.lan",
            qualityProfile = null,
            rootFolder = null,
            is4k = false,
            isDefault = false,
        ),
        ServerService(
            id = null,
            name = "Unreachable",
            type = ServiceType.Sonarr,
            url = null,
            qualityProfile = null,
            rootFolder = null,
            is4k = false,
            isDefault = false,
        ),
    )

private fun policy() = RequestPolicy(SeerrDefaultAccess.RequestWithApproval, movieLimit = RequestLimit(5, 7), tvLimit = RequestLimit(2, 14))

private fun systemInfo() =
    SystemInfo(
        jobs =
            listOf(
                ScheduledJob(id = "plex-recently-added-scan", name = "Recently added scan", running = true, nextRunMillis = null),
                ScheduledJob(id = "download-sync", name = "Download sync", running = false, nextRunMillis = null),
            ),
    )

private fun notifications() =
    NotificationSettings(
        offered = NotificationSignal.entries,
        enabled = setOf(NotificationSignal.PendingRequests, NotificationSignal.RequestAvailable),
        blocked = false,
        lastRunMillis = null,
        nextRunMillis = null,
    )

private fun adminState() =
    SettingsUiState.Ready(
        connection = connection(SignInKind.ApiKey),
        server = server(),
        config =
            ServerConfig(
                general = general(),
                requestPolicy = policy(),
                agents = NotificationAgents(emailEnabled = true, discordEnabled = false),
                system = systemInfo(),
                services = services().take(2),
            ),
        notifications = notifications(),
        app = AppSettings(bugReportUrl = "https://github.com/ScottCooper92/binge-seerr/issues/new", shakeToReport = true),
    )

private fun noActions() =
    SettingsActions(
        onBack = {},
        onEditConnection = {},
        onOpenPage = {},
        onOpenInstance = { _, _ -> },
        onOpenAgent = {},
        onToggleSignal = { _, _ -> },
        onNotificationAccessChanged = {},
        onToggleShakeToReport = {},
        onToggleShareUsageData = {},
        onToggleSendCrashReports = {},
        onDisconnect = {},
    )

@Composable
private fun SettingsFrame(state: SettingsUiState) =
    SettingsScreen(
        state = state,
        actions = noActions(),
        jobs = JobsUiState.Loading,
        jobEvents = emptyFlow(),
        jobActions = JobsActions(onRun = {}, onCancel = {}, onSchedule = { _, _ -> }),
    )

@Composable
private fun JobStatesGroup() {
    val actions = JobsActions(onRun = {}, onCancel = {}, onSchedule = { _, _ -> })
    val jobs = serverJobs()
    Group(
        R.string.settings_group_system,
        listOf(
            jobRow(jobs[0], busy = false, outcome = null, actions) {},
            jobRow(jobs[1], busy = false, outcome = null, actions) {},
            jobRow(jobs[2], busy = false, outcome = JobOutcome.Succeeded, actions) {},
            jobRow(jobs[3], busy = false, outcome = JobOutcome.Failed, actions) {},
            jobRow(jobs[4], busy = true, outcome = null, actions) {},
        ),
    )
}

private fun serverJobs() =
    listOf(
        ServerJob(
            id = "plex-recently-added-scan",
            name = "Recently added scan",
            interval = JobInterval.Short,
            running = true,
            nextRunMillis = null,
        ),
        ServerJob(id = "download-sync", name = "Download sync", interval = JobInterval.Short, running = false, nextRunMillis = null),
        ServerJob(id = "availability-sync", name = "Availability sync", interval = JobInterval.Long, running = false, nextRunMillis = null),
        ServerJob(id = "radarr-scan", name = "Radarr scan", interval = JobInterval.Long, running = false, nextRunMillis = null),
        ServerJob(
            id = "image-cache-cleanup",
            name = "Image cache cleanup",
            interval = JobInterval.Fixed,
            running = false,
            nextRunMillis = null,
        ),
    )
