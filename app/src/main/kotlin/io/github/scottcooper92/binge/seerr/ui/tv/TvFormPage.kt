package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageHosting
import com.binge.designsystem.tv.template.TvTwoPaneCopy
import com.binge.designsystem.tv.template.TvTwoPanePage
import com.binge.designsystem.tv.template.TvTwoPaneSplit
import io.github.scottcooper92.binge.seerr.R
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
    form: @Composable ColumnScope.() -> Unit,
) {
    TvTwoPanePage(
        modifier = modifier,
        split = TvTwoPaneSplit.FixedCopy(dimensionResource(TvR.dimen.tv_two_pane_fixed_pane_width)),
        hosting = TvPageHosting.PreShell,
        divider = false,
        copyAlignment = Alignment.CenterHorizontally,
        copy = {
            TvTwoPaneCopy(
                headline = headline,
                body = body,
                illustration = icon,
                note = note,
                alignment = Alignment.CenterHorizontally,
            )
        },
        action = form,
    )
}

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
        modifier = modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
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
