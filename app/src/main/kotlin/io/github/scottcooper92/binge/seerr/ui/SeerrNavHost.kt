package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.HubActions
import io.github.scottcooper92.binge.seerr.ui.hub.HubScreen
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen

/**
 * The one [NavDisplay]. Every screen is an entry here; the ViewModel-store decorator gives each
 * entry its own store, so a screen's ViewModel lives and dies with its place on the stack rather
 * than with the Activity.
 */
@Composable
fun SeerrNavHost(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeRoute> { HomeEntry(onOpenSection = { section -> backStack.add(SectionRoute(section)) }) }
                entry<SectionRoute> { route ->
                    EmptyScreen(title = stringResource(route.section.titleRes), message = stringResource(R.string.section_coming_soon))
                }
            },
    )
}

/** The home swaps between setup and the hub on the saved credentials, so neither has to know the other. */
@Composable
private fun HomeEntry(
    onOpenSection: (HubSection) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val connected by viewModel.isConnected.collectAsStateWithLifecycle()
    when (connected) {
        null -> LoadingScreen()
        false -> SetupEntry()
        true -> HubEntry(onOpenSection)
    }
}

@Composable
private fun HubEntry(
    onOpenSection: (HubSection) -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The downloading poll and the auto-retry run only while the hub is on screen.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    HubScreen(
        state = state,
        actions =
            HubActions(
                onOpenSection = onOpenSection,
                onRetry = viewModel::recheck,
                onDisconnect = viewModel::disconnect,
            ),
    )
}

@Composable
private fun SetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetupScreen(
        state = state,
        actions =
            SetupActions(
                onEditAddress = viewModel::editAddress,
                onInspect = viewModel::inspect,
                onChangeServer = viewModel::changeServer,
                onEditForm = viewModel::editForm,
                onConnect = viewModel::connect,
                onPlexLaunched = viewModel::plexLaunched,
                onCancelLink = viewModel::cancelLink,
                onRequestPasswordReset = viewModel::requestPasswordReset,
            ),
    )
}
