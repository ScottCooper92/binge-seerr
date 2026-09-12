package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
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
    Scaffold(
        topBar = { BingeTopBar(title = ready?.index?.userName ?: stringResource(R.string.user_settings_title), onBack = actions.onBack) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                UserSettingsUiState.Loading -> LoadingScreen()
                is UserSettingsUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
                is UserSettingsUiState.Ready ->
                    if (state.index.pages.isEmpty()) {
                        EmptyScreen(message = stringResource(R.string.user_settings_none))
                    } else {
                        PageList(state.index.pages, actions.onOpenPage)
                    }
            }
        }
    }
}

@Composable
private fun PageList(
    pages: List<UserSettingsPage>,
    onOpenPage: (UserSettingsPage) -> Unit,
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
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup(
            title = stringResource(R.string.user_settings_title),
            rows = rows,
            modifier = Modifier.padding(dimensionResource(DesR.dimen.screen_content_inset)),
        )
    }
}
