package io.github.scottcooper92.binge.seerr.ui.consent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.DecisionCopy
import com.binge.designsystem.template.DecisionScreen
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.uppercaseLocalised
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen

/** Shows [content] once usage data has an answer, and the question until then. */
@Composable
fun ConsentGate(
    viewModel: ConsentViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state) {
        ConsentUiState.Loading -> LoadingScreen()
        ConsentUiState.Asking -> ConsentScreen(onChoice = viewModel::answer)
        ConsentUiState.Decided -> content()
    }
}

/**
 * The usage data question, laid out as Binge's own analytics step so the two apps ask it the same
 * way: what it is for, what it never touches, how to turn it off, and the two answers. The layout is the
 * design system's [DecisionScreen]; what is this app's own is the copy.
 */
@Composable
fun ConsentScreen(onChoice: (granted: Boolean) -> Unit) {
    DecisionScreen(
        copy =
            DecisionCopy(
                title = stringResource(R.string.consent_title),
                subtitle = stringResource(R.string.consent_subtitle),
                // Already in capitals in the screen's locale: the template's own uppercasing uses the root rules.
                kicker = stringResource(R.string.consent_kicker).uppercaseLocalised(),
            ),
        points = consentPoints(),
        accept = ScreenAction(stringResource(R.string.consent_accept), { onChoice(true) }),
        decline = ScreenAction(stringResource(R.string.consent_decline), { onChoice(false) }),
        hero = Icons.Filled.BarChart,
        heroBadge = Icons.Filled.Lock,
    )
}
