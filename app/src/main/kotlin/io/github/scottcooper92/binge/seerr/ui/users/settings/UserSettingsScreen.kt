package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import com.binge.designsystem.R as DesR

class UserSettingsActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenPage: (UserSettingsPage) -> Unit,
)

/** The index of one user's settings: a row per page the viewer may open. */
@Composable
fun UserSettingsScreen(
    state: UserSettingsUiState,
    actions: UserSettingsActions,
) {
    val ready = state as? UserSettingsUiState.Ready
    ScreenScaffold(title = ready?.index?.userName ?: stringResource(R.string.user_settings_title), onBack = actions.onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding())) {
            val inner = padding.innerPadding()
            when (state) {
                UserSettingsUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is UserSettingsUiState.Error ->
                    ErrorScreen(error = state.error, modifier = Modifier.padding(inner), onRetry = actions.onRetry)
                is UserSettingsUiState.Ready ->
                    if (state.index.pages.isEmpty()) {
                        EmptyScreen(message = stringResource(R.string.user_settings_none), modifier = Modifier.padding(inner))
                    } else {
                        PageList(state.index.pages, actions.onOpenPage, contentPadding = inner)
                    }
            }
        }
    }
}

@Composable
private fun PageList(
    pages: List<UserSettingsPage>,
    onOpenPage: (UserSettingsPage) -> Unit,
    contentPadding: PaddingValues,
) {
    val rows =
        pages.map { page ->
            SettingsRow(
                icon = page.icon(),
                label = stringResource(page.titleRes()),
                detail = stringResource(page.descriptionRes()),
                onClick = { onOpenPage(page) },
            )
        }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        SettingsGroup(
            title = stringResource(R.string.user_settings_title),
            rows = rows,
            modifier = Modifier.padding(dimensionResource(DesR.dimen.screen_content_inset)),
        )
    }
}
