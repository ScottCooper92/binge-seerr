package io.github.scottcooper92.binge.seerr.ui.debug

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.BingeStatus
import io.github.scottcooper92.binge.seerr.ui.hub.BingeTile
import com.binge.designsystem.R as DesR

private class StateRow(
    val status: BingeStatus,
    @StringRes val label: Int,
)

private val rows =
    listOf(
        StateRow(BingeStatus.NotInstalled, R.string.proto_binge_hint_not_installed),
        StateRow(BingeStatus.NotConnected, R.string.proto_binge_hint_not_connected),
        StateRow(BingeStatus.Connected, R.string.proto_binge_hint_connected),
    )

/**
 * Debug builds only: the hub's Binge hint in each of its three states, over local state rather than
 * the store, so closing one here never touches what the real hub has dismissed.
 */
@Composable
internal fun BingeHintPrototypeScreen(onBack: () -> Unit) {
    var dismissed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    BingeScreenScaffold(bar = ScreenBar.Small, title = stringResource(R.string.proto_binge_hint_title), onBack = onBack) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.screenOuterPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(padding.screenInnerPadding())
                    .padding(horizontal = resolvedContentInset()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            rows.forEach { row ->
                Text(stringResource(row.label), style = MaterialTheme.typography.titleSmall)
                BingeTile(
                    status = row.status,
                    hintDismissed = row.status.name in dismissed,
                    onDismissHint = { dismissed = dismissed + row.status.name },
                )
            }
            BingeOutlinedButton(label = stringResource(R.string.proto_binge_hint_reset), onClick = { dismissed = emptySet() })
        }
    }
}
