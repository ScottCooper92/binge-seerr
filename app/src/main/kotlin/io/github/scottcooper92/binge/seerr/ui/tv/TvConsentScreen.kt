package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
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
 * and the two answers in a pinned footer. Share usage data is the primary and takes the arrival focus;
 * Not now sits beside it, one press away and reversible in Settings.
 */
@Composable
internal fun TvConsentScreen(
    onChoice: (granted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    horizontal = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                    vertical = dimensionResource(TvR.dimen.tv_overscan_vertical),
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                text = stringResource(R.string.consent_kicker).uppercase(),
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
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_consent_reassurance_gap))) {
            Text(
                text = stringResource(R.string.tv_consent_reassurance),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.tv_consent_either_choice),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Composable
private fun TvConsentPointRow(point: ConsentPoint) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(point.detailRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = dimensionResource(R.dimen.tv_consent_point_text_gap)),
            )
        }
    }
}
