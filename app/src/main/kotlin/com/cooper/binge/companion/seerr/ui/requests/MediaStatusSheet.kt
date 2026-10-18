package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeChoiceSheetContent
import com.cooper.binge.companion.seerr.R

/**
 * Marking one instance's state. Its own sheet rather than a group inside Manage media, because the
 * pick was already terminal there: it applied and closed the whole sheet.
 *
 * Sequential, never stacked: each sheet owns a window and a scrim, so the host closes Manage media
 * before opening this. It is the design system's choice sheet (#1065), like the pickers beside it. Its
 * subtitle names the instance, because a title the server holds twice has two of these sheets.
 */
@Composable
internal fun MediaStatusSheet(
    instance: MediaInstance,
    onSelect: (MediaStatusChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeChoiceSheet(
        title = stringResource(R.string.media_mark_as),
        subtitle = stringResource(instance.labelRes()),
        caption = stringResource(R.string.media_mark_as_scope_caption),
        choices = statusChoices(),
        selected = instance.selectedChoice(),
        onSelect = onSelect,
        onDismissRequest = onDismiss,
    )
}

/** [MediaStatusSheet]'s body without its window, for a frame and for the actions sheet's page, which drops the handle. */
@Composable
internal fun MediaStatusSheetContent(
    instance: MediaInstance,
    onSelect: (MediaStatusChoice) -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: Boolean = true,
) {
    BingeChoiceSheetContent(
        title = stringResource(R.string.media_mark_as),
        subtitle = stringResource(instance.labelRes()),
        caption = stringResource(R.string.media_mark_as_scope_caption),
        choices = statusChoices(),
        selected = instance.selectedChoice(),
        onSelect = onSelect,
        modifier = modifier,
        dragHandle = dragHandle,
    )
}

@Composable
private fun statusChoices(): BingeChoiceList<MediaStatusChoice> =
    BingeChoiceList.Ready(
        MediaStatusChoice.entries.map { choice -> BingeChoice(choice, stringResource(choice.labelRes()), icon = choice.icon()) },
    )

private fun MediaInstance.selectedChoice(): MediaStatusChoice? = MediaStatusChoice.entries.firstOrNull { it.code == status }
