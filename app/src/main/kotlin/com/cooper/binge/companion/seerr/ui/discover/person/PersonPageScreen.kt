package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.binge.designsystem.template.PersonDetailCallbacks
import com.binge.designsystem.template.PersonDetailScreen
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.links
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.discover.links.ExternalLinksSection
import com.cooper.binge.companion.seerr.ui.discover.title.ShareBarAction
import com.cooper.binge.companion.seerr.ui.rememberAllowLocalNetwork

/** What the person page does: read again, share, open a title and open a link. */
class PersonPageActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryCredits: () -> Unit,
    val onRefresh: () -> Unit,
    val onShare: (name: String, url: String) -> Unit,
    val onOpenUrl: (url: String) -> Unit,
    val onOpenTitle: (kind: TitleKind, tmdbId: Int) -> Unit,
)

/**
 * A person on the shared [PersonDetailScreen]: the portrait and facts, the biography, and the titles they are in as cast and as crew,
 * rails with no paging. Share sits in the bar and the links follow the credits. Loading and a failed load keep the bar, so Back is always there.
 */
@Composable
internal fun PersonPageScreen(
    state: PersonPageUiState,
    actions: PersonPageActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val ready = state as? PersonPageUiState.Ready
    val denied = (state as? PersonPageUiState.Error)?.error == SeerrError.LocalNetworkDenied
    val allow = if (denied) rememberAllowLocalNetwork(actions.onRetry) else null
    val detail = remember(state, resources, locale, allow) { state.toDetailState(resources, locale, allow) }
    PersonDetailScreen(
        state = detail,
        callbacks =
            PersonDetailCallbacks(
                onBack = actions.onBack,
                onRetry = actions.onRetry,
                onOpenCredit = { key -> ready?.creditFor(key)?.let { actions.onOpenTitle(it.kind, it.tmdbId) } },
                onRetryCredits = actions.onRetryCredits,
                onRefresh = actions.onRefresh,
            ),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBarActions = { glassAlpha ->
            if (ready != null) ShareBarAction(glassAlpha) { actions.onShare(ready.person.name, ready.person.webUrl) }
        },
        afterCredits = {
            ready?.person?.let { ExternalLinksSection(it.links(), actions.onOpenUrl) }
        },
    )
}

private fun PersonPageUiState.Ready.creditFor(key: String) =
    (credits as? PersonCreditsState.Ready)?.credits?.let { (it.cast + it.crew).firstOrNull { credit -> credit.key == key } }
