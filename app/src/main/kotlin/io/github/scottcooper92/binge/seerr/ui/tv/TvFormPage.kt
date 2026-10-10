package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageHosting
import com.binge.designsystem.tv.template.TvTwoPaneCopy
import com.binge.designsystem.tv.template.TvTwoPanePage
import com.binge.designsystem.tv.template.TvTwoPaneSplit
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * A form as a television shows it: the design system's [TvTwoPanePage] with the copy centred in a fixed pane
 * on the left — an illustration, what this page is and what to do — and the controls in a scrolling column on
 * the right, so the eye reads once and the remote walks down one column. Every form here is full-screen, so
 * its edges take the overscan margin rather than clearing a rail.
 */
@Composable
internal fun TvFormPage(
    headline: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    note: String? = null,
    pinnedAction: (@Composable RowScope.() -> Unit)? = null,
    actionScrolls: Boolean = true,
    /** A button in the left pane's slot of the button bar: the switch between a page's code and its typed form. */
    copyAction: (@Composable () -> Unit)? = null,
    /**
     * A bar along the bottom of both panes whose height is held whether or not it has buttons in it, so neither the
     * copy nor the form moves when a button comes or goes: [copyAction] on the left, [pinnedAction] on the right.
     */
    buttonBar: Boolean = false,
    /** Whether [pinnedAction] sits centred under the right pane, under something centred there, rather than at its start. */
    pinnedCentered: Boolean = false,
    /** Where the remote lands when it moves into [pinnedAction] from above: the form's commit, not whatever is nearest. */
    pinnedEntry: FocusRequester? = null,
    form: @Composable ColumnScope.() -> Unit,
) {
    if (!buttonBar) {
        TvTwoPanePage(
            modifier = modifier,
            actionScrolls = actionScrolls,
            pinnedAction = pinnedAction,
            split = TvTwoPaneSplit.FixedCopy(dimensionResource(TvR.dimen.tv_two_pane_fixed_pane_width)),
            hosting = TvPageHosting.PreShell,
            divider = false,
            copyAlignment = Alignment.CenterHorizontally,
            copy = {
                TvTwoPaneCopy(headline = headline, body = body, illustration = icon, note = note, alignment = Alignment.CenterHorizontally)
                copyAction?.let { Box(Modifier.padding(top = dimensionResource(DesR.dimen.padding_l))) { it() } }
            },
            action = form,
        )
        return
    }
    Column(modifier = modifier.fillMaxSize()) {
        TvTwoPanePage(
            modifier = Modifier.weight(1f),
            // A scrolling form is laid out here, from the top: a mode with more fields, or a note, grows it downwards
            // and leaves what is above where it was.
            actionScrolls = false,
            split = TvTwoPaneSplit.FixedCopy(dimensionResource(TvR.dimen.tv_two_pane_fixed_pane_width)),
            hosting = TvPageHosting.PreShell,
            divider = false,
            copyAlignment = Alignment.CenterHorizontally,
            copy = {
                TvTwoPaneCopy(headline = headline, body = body, illustration = icon, note = note, alignment = Alignment.CenterHorizontally)
            },
            action =
                if (actionScrolls) {
                    {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = dimensionResource(R.dimen.tv_form_top_inset)),
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(TvR.dimen.tv_two_pane_action_gap)),
                            content = form,
                        )
                    }
                } else {
                    form
                },
        )
        TvFormButtonBar(copyAction = copyAction, pinnedAction = pinnedAction, pinnedCentered = pinnedCentered, pinnedEntry = pinnedEntry)
    }
}

/**
 * The page's buttons as a footer: a full-width band a step lighter than the page, with a hairline along its top, so the
 * switch on the left and the commits on the right read as one strip rather than buttons left at the bottom. Its cells
 * line up with the panes above, and its height is held whether or not they have anything in them.
 */
@Composable
private fun TvFormButtonBar(
    copyAction: (@Composable () -> Unit)?,
    pinnedAction: (@Composable RowScope.() -> Unit)?,
    pinnedCentered: Boolean,
    pinnedEntry: FocusRequester?,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface.copy(alpha = BAR_SURFACE_ALPHA))) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimensionResource(TvR.dimen.tv_button_border_width))
                .background(colors.border.copy(alpha = BAR_HAIRLINE_ALPHA)),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                        end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                        top = dimensionResource(DesR.dimen.padding_m),
                        bottom = dimensionResource(TvR.dimen.tv_overscan_vertical),
                    ).height(dimensionResource(TvR.dimen.tv_button_height)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(TvR.dimen.tv_two_pane_gap)),
        ) {
            Box(Modifier.width(dimensionResource(TvR.dimen.tv_two_pane_fixed_pane_width)), contentAlignment = Alignment.Center) {
                copyAction?.invoke()
            }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { onEnter = { pinnedEntry?.requestFocus() } }
                        .focusGroup(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        dimensionResource(DesR.dimen.padding_sm),
                        if (pinnedCentered) Alignment.CenterHorizontally else Alignment.Start,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) { pinnedAction?.invoke(this) }
        }
    }
}

private const val BAR_SURFACE_ALPHA = 0.55f
private const val BAR_HAIRLINE_ALPHA = 0.18f

/** How a line under a form reads: a fact, a failure, or something that went right. */
internal enum class TvFormNoteTone { Neutral, Error, Success }

/** A line of copy in the form column: a warning under a field, the server's answer, a notice. */
@Composable
internal fun TvFormNote(
    text: String,
    modifier: Modifier = Modifier,
    tone: TvFormNoteTone = TvFormNoteTone.Neutral,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color =
            when (tone) {
                TvFormNoteTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                TvFormNoteTone.Error -> MaterialTheme.colorScheme.error
                TvFormNoteTone.Success -> MaterialTheme.colorScheme.primary
            },
        // A screen reader announces a note when it appears or changes: the board's transient answers come and go in a few
        // seconds, and without this nothing is spoken (#1039). A failure interrupts; anything else waits its turn.
        modifier =
            modifier
                .width(dimensionResource(R.dimen.tv_form_field_width))
                .semantics { liveRegion = if (tone == TvFormNoteTone.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite },
    )
}

/**
 * The frame between two states: setup while the store answers, the picker while the choices load. A loading
 * [TvMessagePage], so the swap to the real message does not reflow, and focus has somewhere to wait meanwhile.
 */
@Composable
internal fun TvLoadingPlate(
    modifier: Modifier = Modifier,
    hosting: TvPageHosting = TvPageHosting.PreShell,
    body: String = stringResource(R.string.tv_loading),
) {
    TvMessagePage(body = body, modifier = modifier, hosting = hosting, loading = true)
}
