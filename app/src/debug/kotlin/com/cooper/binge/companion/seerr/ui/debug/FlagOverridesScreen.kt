package com.cooper.binge.companion.seerr.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R

/** Debug builds only: each feature flag, and a tap cycles its local override (default, on, off). */
@Composable
internal fun FlagOverridesScreen(
    onBack: () -> Unit,
    viewModel: FlagOverridesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BingeScreenScaffold(bar = ScreenBar.Small, title = stringResource(R.string.proto_flags_title), onBack = onBack) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.screenOuterPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(padding.screenInnerPadding())
                    .padding(horizontal = resolvedContentInset()),
        ) {
            val rows = (state as? FlagOverridesUiState.Ready)?.rows.orEmpty()
            ItemGroup(
                title = null,
                rows =
                    rows.map { row ->
                        ListItem(
                            icon = Icons.Filled.Flag,
                            label = row.flag.key,
                            detail =
                                stringResource(
                                    when (row.override) {
                                        null -> if (row.effective) R.string.proto_flag_default_on else R.string.proto_flag_default_off
                                        true -> R.string.proto_flag_forced_on
                                        false -> R.string.proto_flag_forced_off
                                    },
                                ),
                            onClick = { viewModel.cycle(row) },
                        )
                    },
            )
        }
    }
}
