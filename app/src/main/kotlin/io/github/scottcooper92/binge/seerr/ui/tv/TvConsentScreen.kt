package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.binge.designsystem.uppercaseLocalised
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentPoint
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentPoints
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentUiState
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentViewModel
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** The phone's consent gate on a television: the same question, before setup and the rail. */
@Composable
internal fun TvConsentGate(
    viewModel: ConsentViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state) {
        ConsentUiState.Loading -> TvLoadingPlate()
        ConsentUiState.Asking -> TvConsentScreen(onChoice = viewModel::answer)
        ConsentUiState.Decided -> content()
    }
}

/**
 * Binge's TV analytics step, for this app: the heading and reassurance beside a read-out points card,
 * and the two answers in a footer at the bottom. Share usage data is the primary and takes the arrival focus;
 * Not now sits beside it, one press away and reversible in Settings.
 *
 * Nothing here is cut short (#1258). The page is as tall as the window when it fits and scrolls when it does not, a
 * longer translation or a larger font, and each point is a focusable read-out, so the D-pad can walk up to one the
 * scroll has moved away. On a consent screen a hidden line is a line the user agreed to without reading.
 */
@Composable
internal fun TvConsentScreen(
    onChoice: (granted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TvConsentPage(onChoice = onChoice, arrival = arrival, minHeight = maxHeight)
    }
}

@Composable
private fun TvConsentPage(
    onChoice: (granted: Boolean) -> Unit,
    arrival: TvArrivalFocus,
    minHeight: Dp,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(
                    horizontal = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                    vertical = dimensionResource(TvR.dimen.tv_overscan_vertical),
                ),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(R.dimen.tv_consent_band_spacing)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
        ) {
            TvConsentCopy(modifier = Modifier.weight(1f))
            TvConsentPointsCard(modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_consent_footer_gap)),
        ) {
            Box(modifier = Modifier.weight(1f))
            TvButton(
                label = stringResource(R.string.consent_accept),
                onClick = { onChoice(true) },
                style = TvButtonStyle.Primary,
                modifier = Modifier.tvArrivalTarget(arrival),
            )
            TvButton(label = stringResource(R.string.consent_decline), onClick = { onChoice(false) })
        }
    }
}

/** Kicker, title and subtitle, then what either answer means. Nothing here takes focus. */
@Composable
private fun TvConsentCopy(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_consent_copy_gap))) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_consent_heading_gap))) {
            Text(
                text = stringResource(R.string.consent_kicker).uppercaseLocalised(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.consent_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.tv_consent_reassurance),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The phone's three points as a read-out, the last reworded because a remote is a press, not a tap. */
@Composable
private fun TvConsentPointsCard(modifier: Modifier = Modifier) {
    val points =
        ConsentPoints.mapIndexed { index, point ->
            if (index ==
                ConsentPoints.lastIndex
            ) {
                ConsentPoint(point.icon, R.string.tv_consent_point_change_title, point.detailRes)
            } else {
                point
            }
        }
    Column(
        modifier = modifier.fillMaxWidth().clip(BingeShapes.ListCard).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        points.forEachIndexed { index, point ->
            TvConsentPointRow(point)
            if (index < points.lastIndex) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(dimensionResource(DesR.dimen.hairline_thickness))
                            .background(MaterialTheme.colorScheme.borderVariant),
                )
            }
        }
    }
}

/** A read-out, never a press: focusable only so the D-pad can reach a point the page has scrolled away. */
@Composable
private fun TvConsentPointRow(point: ConsentPoint) {
    var focused by remember { mutableStateOf(false) }
    val content = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurface)
    val muted = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tvFocusFill(isFocused = focused, shape = BingeShapes.TvListItem)
                .tvClickable(enabled = false, onFocusChanged = { focused = it }, onClick = {})
                // Merged so the node that carries focus is the one that reads.
                .semantics(mergeDescendants = true) {}
                .padding(
                    horizontal = dimensionResource(R.dimen.tv_consent_point_padding_h),
                    vertical = dimensionResource(R.dimen.tv_consent_point_padding_v),
                ),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_consent_point_gap)),
    ) {
        Box(
            modifier =
                Modifier
                    .size(dimensionResource(R.dimen.tv_consent_point_badge))
                    .clip(BingeShapes.Large)
                    .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = point.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimensionResource(R.dimen.tv_consent_point_icon)),
            )
        }
        Column {
            Text(
                text = stringResource(point.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
            Text(
                text = stringResource(point.detailRes),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(top = dimensionResource(R.dimen.tv_consent_point_text_gap)),
            )
        }
    }
}
