package com.cooper.binge.companion.seerr.ui.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheetContent
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.flags.WatchPlayer

/** The row that says where Watch plays a title, and opens [WatchPlayerSheet] to change it. */
@Composable
internal fun watchPlayerRow(
    player: WatchPlayer,
    onOpen: () -> Unit,
): ListItem =
    ListItem(
        icon = Icons.Filled.PlayArrow,
        label = stringResource(R.string.settings_watch_player),
        detail = stringResource(player.labelRes()),
        onClick = onOpen,
    )

/** The ways Watch can play a title, one tap each: it saves as chosen, like every setting here. */
@Composable
internal fun WatchPlayerSheet(
    selected: WatchPlayer,
    onSelect: (WatchPlayer) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = WatchPlayer.entries.map { BingeChoice(it, stringResource(it.labelRes()), icon = it.icon()) }
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        BingeChoiceSheetContent(
            title = stringResource(R.string.settings_watch_player),
            choices = BingeChoiceList.Ready(choices),
            selected = selected,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
        )
    }
}

@StringRes
internal fun WatchPlayer.labelRes(): Int =
    when (this) {
        WatchPlayer.Automatic -> R.string.settings_watch_player_automatic
        WatchPlayer.Ask -> R.string.settings_watch_player_ask
        WatchPlayer.Ephyra -> R.string.settings_watch_player_ephyra
        WatchPlayer.Binge -> R.string.settings_watch_player_binge
        WatchPlayer.MediaServer -> R.string.settings_watch_player_media_server
    }

private fun WatchPlayer.icon(): ImageVector =
    when (this) {
        WatchPlayer.Automatic -> Icons.Filled.AutoAwesome
        WatchPlayer.Ask -> Icons.Filled.QuestionMark
        WatchPlayer.Ephyra -> Icons.Filled.PlayCircle
        WatchPlayer.Binge -> Icons.Filled.Tv
        WatchPlayer.MediaServer -> Icons.Filled.Dns
    }
