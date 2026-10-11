package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.DecisionCopy
import com.binge.designsystem.DecisionPoint
import com.binge.designsystem.tv.template.TvDecisionPage
import com.binge.designsystem.tv.template.TvPageHosting
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentPoints
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentUiState
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentViewModel

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
 * Binge's TV analytics step, for this app, on the design system's [TvDecisionPage] (#1055): the heading and
 * reassurance beside a read-out points card, and the two answers under it. Share usage data is the primary and takes
 * the arrival focus; Not now sits beside it, one press away and reversible in Settings. What is this app's own is the
 * copy.
 *
 * Each point is a focusable read-out, so ↑ from the answers walks a card taller than the panel into view (#1258). On
 * a consent screen a hidden line is a line the user agreed to without reading.
 */
@Composable
internal fun TvConsentScreen(
    onChoice: (granted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvDecisionPage(
        copy =
            DecisionCopy(
                title = stringResource(R.string.consent_title),
                subtitle = stringResource(R.string.consent_subtitle),
                kicker = stringResource(R.string.consent_kicker),
                note = stringResource(R.string.tv_consent_reassurance),
            ),
        points = tvConsentPoints(),
        acceptLabel = stringResource(R.string.consent_accept),
        declineLabel = stringResource(R.string.consent_decline),
        onAccept = { onChoice(true) },
        onDecline = { onChoice(false) },
        modifier = modifier,
        // A gate before setup and the rail, so the page places its own first focus on Share usage data.
        hosting = TvPageHosting.PreShell,
    )
}

/** The phone's three points, the last reworded because a remote is a press, not a tap. */
@Composable
private fun tvConsentPoints(): List<DecisionPoint> =
    ConsentPoints.mapIndexed { index, point ->
        val title = if (index == ConsentPoints.lastIndex) R.string.tv_consent_point_change_title else point.titleRes
        DecisionPoint(icon = point.icon, title = stringResource(title), detail = stringResource(point.detailRes))
    }
