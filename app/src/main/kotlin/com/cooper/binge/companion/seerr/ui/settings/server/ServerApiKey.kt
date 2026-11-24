package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.PeekingListSheet
import com.binge.designsystem.R as DesR
import com.cooper.binge.companion.seerr.core.ui.R as UiR

class ApiKeyActions(
    val onToggleReveal: () -> Unit,
    val onCopy: (String) -> Unit,
    val onRegenerate: () -> Unit,
)

/** How many of the key's last characters its row shows while it is hidden: enough to tell two keys apart, no more. */
private const val KEY_TAIL = 4

/**
 * The server's API key as a row, hidden but for its last characters. A tap opens it in a sheet of list rows: the key
 * itself with a copy button and a reveal toggle beside it, and Regenerate, which asks first since the old key stops working at once.
 */
@Composable
internal fun apiKeyItem(
    apiKey: ApiKeyState,
    actions: ApiKeyActions,
    enabled: Boolean,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.server_settings_api_key)
    val hidden = apiKey.hidden()
    if (open) {
        PeekingListSheet(title = title, onDismiss = { open = false }) {
            ApiKeyRows(apiKey, actions, onRegenerate = { confirming = true })
        }
    }
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.server_settings_api_key_regenerate_title),
            message = stringResource(R.string.server_settings_api_key_regenerate_message),
            confirmLabel = stringResource(R.string.server_settings_api_key_regenerate),
            destructive = true,
            onConfirm = {
                confirming = false
                actions.onRegenerate()
            },
            onDismissRequest = { confirming = false },
        )
    }
    return ListItem(
        icon = Icons.Filled.Key,
        label = title,
        detail = hidden ?: stringResource(UiR.string.settings_value_unknown),
        enabled = enabled,
        onClick = { open = true },
    )
}

/** The key's sheet: the key with its copy button and reveal toggle, and Regenerate, which [onRegenerate] confirms first. */
@Composable
internal fun ApiKeyRows(
    apiKey: ApiKeyState,
    actions: ApiKeyActions,
    onRegenerate: () -> Unit,
) {
    ItemGroup(
        title = null,
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        rows =
            listOf(
                ListItem(
                    icon = Icons.Filled.Key,
                    label = stringResource(R.string.server_settings_api_key),
                    detail =
                        if (apiKey.revealed) {
                            apiKey.key.inLines()
                        } else {
                            apiKey.hidden() ?: stringResource(UiR.string.settings_value_unknown)
                        },
                    // A revealed key is the thing the user came to read, so it wraps rather than ending in an ellipsis.
                    supportingMaxLines = if (apiKey.revealed) 6 else 1,
                    clickable = false,
                    trailingContent = {
                        Row {
                            if (apiKey.key.isNotEmpty()) {
                                ExpressiveIconButton(
                                    icon = Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.server_settings_api_key_copy),
                                    onClick = { actions.onCopy(apiKey.key) },
                                )
                            }
                            ExpressiveIconButton(
                                icon = if (apiKey.revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription =
                                    stringResource(
                                        if (apiKey.revealed) {
                                            R.string.server_settings_api_key_hide
                                        } else {
                                            R.string.server_settings_api_key_show
                                        },
                                    ),
                                onClick = actions.onToggleReveal,
                            )
                        }
                    },
                ),
                ListItem(
                    icon = Icons.Filled.Refresh,
                    iconTint = BingeSentiment.Negative.fill(),
                    label = stringResource(R.string.server_settings_api_key_regenerate),
                    detail = stringResource(R.string.server_settings_api_key_regenerate_detail),
                    loading = apiKey.regenerating,
                    destination = ListItemDestination.Action,
                    onClick = onRegenerate,
                ),
            ),
    )
    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
}

/** How many characters of a revealed key go on one line. */
private const val KEY_LINE = 16

/**
 * The key cut into lines of a few characters. A key has no spaces, so on one line it would be one word as wide as the row and
 * leave its buttons no room; in short lines they keep theirs. Copy still takes the key itself.
 */
private fun String.inLines(): String = chunked(KEY_LINE).joinToString("\n")

/** "•••• abcd": enough of the key to tell two apart, or null before one has been read. */
@Composable
private fun ApiKeyState.hidden(): String? = key.maskedKey()

/** Any key as only its last characters, "•••• abcd"; null when there is none. */
@Composable
internal fun String.maskedKey(): String? =
    takeLast(KEY_TAIL).takeIf { it.isNotEmpty() }?.let { stringResource(R.string.server_settings_api_key_hidden, it) }
