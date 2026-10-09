package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.Regions
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import com.binge.designsystem.R as DesR

/** A region code is two letters, ISO 3166-1 alpha-2, as the server keeps it. */
private const val REGION_CODE_LENGTH = 2

/**
 * A region setting as a list row: its current value named in the device's language, so the page needs no list to draw
 * it. The server's list is read only when the sheet opens ([onOpen]); the sheet shows it being read, then the choices
 * with "All regions" (the server's blank, no filter) first, or a way to retry or type the code if the server can't
 * send its list. A user's region passes [serverDefault], the server's own region: blank then reads "Default (…)", and
 * "All regions" is [Regions.ALL].
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
    serverDefault: String? = null,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    val allRegions = stringResource(R.string.server_settings_all_regions)
    val defaultLabel =
        serverDefault?.let { region ->
            stringResource(R.string.settings_value_server_default, region.takeIf { it.isNotBlank() }?.let(Regions::name) ?: allRegions)
        }
    if (open) {
        val pick = { code: String ->
            onSelect(code)
            open = false
        }
        if (choices == ListChoices.Failed) {
            PeekingListSheet(title = title, onDismiss = { open = false }) { RegionUnavailable(value, onRetry = onOpen, onUse = pick) }
        } else {
            val all = (choices as? ListChoices.Ready)?.entries?.map { it.code }
            BingeChoiceSheet(
                title = title,
                choices =
                    all?.let { codes ->
                        BingeChoiceList.Ready(
                            Regions.choices(codes, value, allRegions, defaultLabel).map { (code, label) ->
                                BingeChoice(code, label, mark = flagOf(code))
                            },
                        )
                    } ?: BingeChoiceList.Loading,
                selected = value.trim(),
                onSelect = pick,
                onDismiss = { open = false },
                // The server's default and "all regions" first, then the device's regions and popular ones.
                suggested = listOf("", Regions.ALL) + suggestedRegions(),
            )
        }
    }
    return ListItem(
        icon = icon,
        // The chosen region's flag in the icon's place, or a globe for all regions and the server's default.
        leadingContent = { RegionFlag(value.trim()) },
        label = title,
        detail =
            when (val code = value.trim()) {
                "" -> defaultLabel ?: allRegions
                Regions.ALL -> allRegions
                else -> Regions.name(code)
            },
        clickable = enabled,
        disabled = !enabled,
        onClick = {
            open = true
            onOpen()
        },
    )
}

/**
 * The wait while a list is read, in the sheet that will show it. It is a window tall, as the list will be: a sheet holding
 * only a spinner fits whole, so it would settle fully open and then fill the screen when the list arrived. This tall, it
 * peeks at half height like the list, and the list arrives into a sheet that stays where it is.
 */
@Composable
internal fun ListLoading() {
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    BingeLoadingIndicator(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = windowHeight)
                .wrapContentSize(Alignment.TopCenter)
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
