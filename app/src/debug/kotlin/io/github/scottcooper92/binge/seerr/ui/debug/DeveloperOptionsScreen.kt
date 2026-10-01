package io.github.scottcooper92.binge.seerr.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding

/**
 * Debug builds only, reached from the hub's last row: tools for working on the app rather than
 * using it. For now that is UI previews: screens rendered over sample data rather than a server.
 */
@Composable
internal fun DeveloperOptionsScreen(
    onBack: (() -> Unit)?,
    onOpenManageSheetPrototype: () -> Unit,
    onOpenRequestCardsPrototype: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.debug_developer_options), onBack = onBack) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.outerPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(padding.innerPadding())
                    .padding(horizontal = resolvedContentInset()),
        ) {
            SettingsGroup(
                title = stringResource(R.string.debug_group_prototypes),
                rows =
                    listOf(
                        SettingsRow(
                            icon = Icons.Filled.ViewAgenda,
                            label = stringResource(R.string.proto_manage_sheet_title),
                            detail = stringResource(R.string.proto_manage_sheet_detail),
                            onClick = onOpenManageSheetPrototype,
                        ),
                        SettingsRow(
                            icon = Icons.Filled.ViewAgenda,
                            label = stringResource(R.string.proto_request_cards_title),
                            detail = stringResource(R.string.proto_request_cards_detail),
                            onClick = onOpenRequestCardsPrototype,
                        ),
                    ),
            )
        }
    }
}
