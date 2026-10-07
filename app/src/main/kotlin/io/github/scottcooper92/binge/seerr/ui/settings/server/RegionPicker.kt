package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.Regions
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import io.github.scottcooper92.binge.seerr.ui.users.settings.ChoiceRows
import com.binge.designsystem.R as DesR

/** A region code is two letters, ISO 3166-1 alpha-2, as the server keeps it. */
private const val REGION_CODE_LENGTH = 2

/**
 * A region setting as a list row: its current value named in the device's language, so the page needs no list to draw
 * it. The server's list is read only when the sheet opens ([onOpen]); the sheet shows it being read, then the choices
 * with "All regions" (the server's blank, no filter) first, or a way to retry or type the code if the server can't
 * send its list.
 */
@Composable
internal fun regionSettingItem(
    icon: ImageVector,
    title: String,
    value: String,
    choices: ListChoices?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSelect: (String) -> Unit,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    val allRegions = stringResource(R.string.server_settings_all_regions)
    if (open) {
        val pick = { code: String ->
            onSelect(code)
            open = false
        }
        PeekingListSheet(title = title, onDismiss = { open = false }) {
            when (choices) {
                is ListChoices.Ready ->
                    ChoiceRows(
                        icon,
                        Regions.choices(choices.entries.map { it.code }, value, allRegions),
                        value.trim(),
                        pick,
                    )
                ListChoices.Failed -> RegionUnavailable(value, onRetry = onOpen, onUse = pick)
                ListChoices.Loading, null -> ListLoading()
            }
        }
    }
    return ListItem(
        icon = icon,
        label = title,
        detail = value.trim().takeIf { it.isNotEmpty() }?.let(Regions::name) ?: allRegions,
        clickable = enabled,
        disabled = !enabled,
        onClick = {
            open = true
            onOpen()
        },
    )
}

/** The wait while a list is read, in the sheet that will show it. */
@Composable
internal fun ListLoading() {
    BingeLoadingIndicator(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .padding(dimensionResource(DesR.dimen.padding_l)),
    )
}

/** The server couldn't send its regions: try again, or type the two-letter code as the field used to take it. */
@Composable
private fun RegionUnavailable(
    value: String,
    onRetry: () -> Unit,
    onUse: (String) -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf(value) }
    TextEntrySurface(
        title = stringResource(R.string.server_settings_region_code),
        value = typed,
        onValueChange = { typed = it.uppercase() },
        onSubmit = { onUse(typed.trim()) },
        onCancel = onRetry,
        submitLabel = stringResource(R.string.server_settings_list_use),
        cancelLabel = stringResource(R.string.action_try_again),
        submitEnabled = typed.trim().let { it.isEmpty() || (it.length == REGION_CODE_LENGTH && it.all(Char::isLetter)) },
        minLines = 1,
        maxLines = 1,
        header = {
            Text(
                stringResource(R.string.server_settings_regions_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
