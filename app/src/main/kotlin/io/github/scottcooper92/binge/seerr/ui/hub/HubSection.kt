package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R

/**
 * The hub's manage rows, in display order. Each is gated on what the server has (the profile) and
 * what the user may do (their permissions), so a restricted user is not offered a door the server
 * would slam; the server's own 401/403 stays the backstop. A new section is one entry here plus
 * its route and screen.
 */
enum class HubSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val badgeCount: (HubOverview) -> Int? = { null },
    val isVisible: (HubOverview) -> Boolean = { true },
) {
    Requests(
        titleRes = R.string.hub_section_requests,
        descriptionRes = R.string.hub_section_requests_desc,
        icon = Icons.Filled.Inbox,
        badgeCount = { it.pendingRequestCount },
    ),
    Issues(
        titleRes = R.string.hub_section_issues,
        descriptionRes = R.string.hub_section_issues_desc,
        icon = Icons.Filled.ReportProblem,
        badgeCount = { it.openIssueCount },
        isVisible = { it.hasIssues && it.permissions.canSeeIssues },
    ),
    Users(
        titleRes = R.string.hub_section_users,
        descriptionRes = R.string.hub_section_users_desc,
        icon = Icons.Filled.People,
        badgeCount = { it.userCount },
        isVisible = { it.permissions.canManageUsers },
    ),
    Blocklist(
        titleRes = R.string.hub_section_blocklist,
        descriptionRes = R.string.hub_section_blocklist_desc,
        icon = Icons.Filled.Block,
        badgeCount = { it.blocklistCount },
        isVisible = { it.hasBlocklist && it.permissions.canViewBlocklist },
    ),
    Settings(
        titleRes = R.string.hub_section_settings,
        descriptionRes = R.string.hub_section_settings_desc,
        icon = Icons.Filled.Settings,
        isVisible = { it.permissions.canManageSettings },
    ),
}

/** The sections the overview admits, in order. */
fun HubOverview.visibleSections(): List<HubSection> = HubSection.entries.filter { it.isVisible(this) }

/** Each section reads as its own accent: amber, red, blue, purple, teal. */
@Composable
internal fun HubSection.iconTint(): Color =
    when (this) {
        HubSection.Requests -> BingeSentiment.Caution.fill()
        HubSection.Issues -> BingeSentiment.Negative.fill()
        HubSection.Users -> BingeSentiment.Info.fill()
        HubSection.Blocklist -> BingeSentiment.Neutral.fill()
        HubSection.Settings -> MaterialTheme.colorScheme.tertiary
    }

/** The badge's tint: Caution for the actionable backlogs, Neutral for plain totals. */
@Composable
internal fun HubSection.badgeTint(): Color =
    when (this) {
        HubSection.Requests, HubSection.Issues -> BingeSentiment.Caution.accent()
        else -> BingeSentiment.Neutral.accent()
    }
