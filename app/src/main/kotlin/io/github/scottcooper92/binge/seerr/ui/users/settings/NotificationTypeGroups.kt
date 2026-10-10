package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R

/**
 * The events a notification agent is sent, as the web client's checklist: the request ones, then the issue ones.
 * [offered] is the events this page lists, [types] the bitmask of those switched on, and [onToggle] flips one event's bit.
 * Shared by the user's notification page and the server's agent page, which hold the mask in different drafts.
 */
@Composable
internal fun NotificationTypeGroups(
    offered: List<NotificationType>,
    types: Int,
    enabled: Boolean,
    onToggle: (bit: Int) -> Unit,
) {
    listOf(false, true).forEach { issues ->
        ItemGroup(
            title =
                stringResource(
                    if (issues) R.string.server_settings_agent_types_issues else R.string.server_settings_agent_types_requests,
                ),
            rows =
                offered.filter { it.issue == issues }.map { type ->
                    editorToggle(Icons.Filled.Notifications, stringResource(type.labelRes()), types and type.bit != 0, enabled) {
                        onToggle(type.bit)
                    }
                },
        )
    }
}
