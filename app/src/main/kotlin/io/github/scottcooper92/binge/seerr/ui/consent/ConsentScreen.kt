package io.github.scottcooper92.binge.seerr.ui.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.centredReadingColumn
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.isLandscape
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** Landscape's two panes: the hero, heading and answers against the points card. */
private const val LANDSCAPE_HEADER_WEIGHT = 2f
private const val LANDSCAPE_CONTENT_WEIGHT = 3f

/** Shows [content] once usage data has an answer, and the question until then. */
@Composable
fun ConsentGate(
    viewModel: ConsentViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state) {
        ConsentUiState.Loading -> Unit
        ConsentUiState.Asking -> ConsentScreen(onChoice = viewModel::answer)
        ConsentUiState.Decided -> content()
    }
}

/**
 * The usage data question, laid out as Binge's own analytics step so the two apps ask it the same
 * way: what it is for, what it never touches, how to turn it off, and the two answers.
 */
@Composable
fun ConsentScreen(onChoice: (granted: Boolean) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (isLandscape()) ConsentLandscape(onChoice) else ConsentPortrait(onChoice)
    }
}

/** Hero, heading and points in one scroll; the answers stay pinned below. */
@Composable
private fun ConsentPortrait(onChoice: (Boolean) -> Unit) {
    Column(
        modifier =
            Modifier
                .centredReadingColumn(dimensionResource(R.dimen.consent_content_max_width))
                .padding(horizontal = dimensionResource(DesR.dimen.padding_l)),
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PrivacyHero(
                modifier =
                    Modifier.padding(
                        top = dimensionResource(DesR.dimen.padding_s),
                        bottom = dimensionResource(DesR.dimen.padding_l),
                    ),
            )
            ConsentHeading()
            ConsentPointsCard(modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_l)))
        }
        ConsentFooter(onChoice)
    }
}

/**
 * Wide but short: the heading and answers on the left, the points card on the right with its own
 * scroll. The hero is dropped, because the short window cannot spare its height.
 */
@Composable
private fun ConsentLandscape(onChoice: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = dimensionResource(DesR.dimen.padding_l)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        Column(modifier = Modifier.weight(LANDSCAPE_HEADER_WEIGHT).fillMaxHeight()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ConsentHeading(modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_l)))
            }
            ConsentFooter(onChoice)
        }
        Column(
            modifier =
                Modifier
                    .weight(LANDSCAPE_CONTENT_WEIGHT)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(top = dimensionResource(R.dimen.consent_landscape_points_top)),
        ) {
            ConsentPointsCard()
        }
    }
}

@Composable
private fun ConsentHeading(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = dimensionResource(DesR.dimen.padding_m))) {
        Text(
            text = stringResource(R.string.consent_kicker).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_sm)),
        )
        Text(
            text = stringResource(R.string.consent_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_sm)),
        )
        Text(
            text = stringResource(R.string.consent_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Decline as a quiet text button above the primary, as Binge's step has them. */
@Composable
private fun ConsentFooter(onChoice: (Boolean) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(DesR.dimen.padding_m), bottom = dimensionResource(DesR.dimen.padding_l)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = { onChoice(false) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.consent_decline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BingeFilledButton(
            label = stringResource(R.string.consent_accept),
            onClick = { onChoice(true) },
            modifier = Modifier.fillMaxWidth().padding(top = dimensionResource(DesR.dimen.padding_xs)),
        )
    }
}
