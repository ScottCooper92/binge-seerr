package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.DecisionCopy
import com.binge.designsystem.DecisionPoint
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.tv.template.TvDecisionPage
import com.binge.designsystem.tv.template.TvPageHosting
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.consent.ConsentViewModel
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.core.uiandroid.R as CoreR

/** The phone's consent gate on a television: the same question, before setup and Home. */
@Composable
internal fun TvConsentGate(
    viewModel: ConsentViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    when (consent) {
        null -> TvLoadingPlate()
        AnalyticsConsent.UNDECIDED -> TvConsentScreen(onChoice = viewModel::answer)
        AnalyticsConsent.GRANTED, AnalyticsConsent.DENIED -> content()
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
                title = stringResource(CoreR.string.consent_title),
                subtitle = stringResource(CoreR.string.consent_subtitle),
                kicker = stringResource(CoreR.string.consent_kicker),
                note = stringResource(R.string.tv_consent_reassurance),
            ),
        points = tvConsentPoints(),
        accept = ScreenAction(stringResource(CoreR.string.consent_accept), onClick = { onChoice(true) }),
        decline = ScreenAction(stringResource(CoreR.string.consent_decline), onClick = { onChoice(false) }),
        modifier = modifier,
        // A gate before setup and Home, so the page places its own first focus on Share.
        hosting = TvPageHosting.PreShell,
    )
}

/**
 * The shared question's three points, the last in this app's own television wording, because a remote is a press, not a tap, and
 * because the TV keeps these switches in Settings.
 */
@Composable
private fun tvConsentPoints(): List<DecisionPoint> =
    listOf(
        DecisionPoint(
            Icons.Filled.BarChart,
            stringResource(CoreR.string.consent_point_shared_title),
            stringResource(CoreR.string.consent_point_shared_detail),
        ),
        DecisionPoint(
            Icons.Filled.VisibilityOff,
            stringResource(CoreR.string.consent_point_never_title),
            stringResource(CoreR.string.consent_point_never_detail),
        ),
        DecisionPoint(
            Icons.Filled.ToggleOff,
            stringResource(R.string.tv_consent_point_change_title),
            stringResource(R.string.tv_consent_point_change_detail),
        ),
    )
