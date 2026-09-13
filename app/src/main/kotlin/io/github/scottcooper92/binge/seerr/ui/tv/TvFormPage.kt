package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvIllustration
import com.binge.designsystem.tv.component.TvMessagePlate
import com.binge.designsystem.tv.focus.TvStableFocusScroll
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * A form as a television shows it: the copy on the left — an illustration, what this page is and what to
 * do — and the controls in a scrolling column on the right, so the eye reads once and the remote walks
 * down one column. The shape Binge's settings pane takes, applied to a page that *is* one setting.
 *
 * The column scrolls rather than fits: a column that cannot scroll starves its children, and a starved
 * field is still focusable, so ↓ lands on a sliver the user cannot read.
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
    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    horizontal = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                    vertical = dimensionResource(TvR.dimen.tv_overscan_vertical),
                ),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_form_pane_gap)),
    ) {
        Column(
            modifier = Modifier.width(dimensionResource(R.dimen.tv_form_copy_width)).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m), Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvIllustration(icon = icon)
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TvStableFocusScroll {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = dimensionResource(TvR.dimen.tv_focus_ring_bleed)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_form_section_gap), Alignment.CenterVertically),
                    content = form,
                )
            }
        }
    }
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

/** The frame between two states: setup while the store answers, the picker while the choices load. */
@Composable
internal fun TvLoadingPlate(
    modifier: Modifier = Modifier,
    body: String = stringResource(R.string.tv_loading),
) {
    TvMessagePlate(
        body = body,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        alignment = Alignment.Center,
    )
}
