package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvExitFocusGroup
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import com.binge.designsystem.tv.focus.tvStartDirectionKey
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

private const val SCRIM_ALPHA = 0.6f
private const val DISABLED_ROW_ALPHA = 0.5f

/**
 * An end-edge, focus-trapped action sheet over a scrim: the ten-foot counterpart of the phone's bottom
 * sheet, and the overlay the lists put a row's actions on. It hands [content] the entry requester; each
 * step the content shows pins it to its first row and pulls it there with [TvActionSheetStepFocus]. Back,
 * and the key pointing away from the panel, both call [onDismiss]; the caller owns the visibility flag
 * and returns focus to what opened the sheet.
 */
@Composable
internal fun TvActionSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(entryFocus: FocusRequester) -> Unit,
) {
    val entryFocus = remember { FocusRequester() }
    val dismissKey = tvStartDirectionKey()
    BackHandler(onBack = onDismiss)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == dismissKey) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
    ) {
        TvActionSheetPanel(modifier = Modifier.align(Alignment.CenterEnd)) { content(entryFocus) }
    }
}

/**
 * Pulls the sheet's entry focus to the step that has just come into composition: the actions when the
 * sheet appears, and again when Cancel on a confirm step brings them back. Requested from the step, not
 * the sheet, because a step swap recomposes the rows without the sheet itself re-entering.
 */
@Composable
internal fun TvActionSheetStepFocus(entryFocus: FocusRequester) {
    LaunchedEffect(Unit) { runCatching { entryFocus.requestFocus() } }
}

/** The sheet's visible panel — stateless, so a preview renders it without the scrim's focus request. */
@Composable
internal fun TvActionSheetPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(dimensionResource(R.dimen.tv_action_sheet_width))
                .background(MaterialTheme.colorScheme.surface)
                .tvExitFocusGroup()
                .padding(dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        content = content,
    )
}

@Composable
internal fun TvActionSheetTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_xs)),
    )
}

@Composable
internal fun TvActionSheetBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_xs)),
    )
}

/**
 * One action. Focus is the fill; a [destructive] row rests in the error colour so it reads as one before it
 * is reached. [enabled] false renders a read-out that stays out of the focus order.
 */
@Composable
internal fun TvActionSheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    initiallyFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val resting =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ROW_ALPHA)
            destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BingeShapes.TvListItem)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.TvListItem)
                .then(if (enabled) Modifier.tvClickable(onFocusChanged = { focused = it }, onClick = onClick) else Modifier)
                .padding(
                    horizontal = dimensionResource(DesR.dimen.padding_l),
                    vertical = dimensionResource(DesR.dimen.padding_m),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = tvFocusContentColor(isFocused = focused, resting = resting),
        )
    }
}

/**
 * The sheet's second step for an action that deletes or blocks: what is about to happen, then the
 * confirm and Cancel rows, with focus landing on Cancel so a stray OK does nothing.
 */
@Composable
internal fun ColumnScope.TvActionSheetConfirm(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    entryFocus: FocusRequester,
    initialCancelFocused: Boolean = false,
) {
    TvActionSheetTitle(title)
    TvActionSheetBody(message)
    TvActionSheetRow(label = confirmLabel, onClick = onConfirm, destructive = true)
    TvActionSheetRow(
        label = stringResource(DesR.string.action_cancel),
        onClick = onCancel,
        initiallyFocused = initialCancelFocused,
        modifier = Modifier.focusRequester(entryFocus),
    )
    TvActionSheetStepFocus(entryFocus)
}
