package io.github.scottcooper92.binge.seerr.ui.settings.server

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
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import com.binge.designsystem.R as DesR

class ApiKeyActions(
    val onToggleReveal: () -> Unit,
    val onCopy: (String) -> Unit,
    val onRegenerate: () -> Unit,
)

/** How many of the key's last characters its row shows while it is hidden: enough to tell two keys apart, no more. */
private const val KEY_TAIL = 4

/**
 * The server's API key as a row, hidden but for its last characters. A tap opens it in a sheet of list rows: the key
 * itself with a reveal toggle, Copy, and Regenerate, which asks first since the old key stops working at once.
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
            onDismiss = { confirming = false },
        )
    }
    return ListItem(
        icon = Icons.Filled.Key,
        label = title,
        detail = hidden ?: stringResource(R.string.settings_value_unknown),
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}

/** The key's sheet: the key with its reveal toggle, Copy, and Regenerate, which [onRegenerate] confirms first. */
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
                    detail = if (apiKey.revealed) apiKey.key else apiKey.hidden() ?: stringResource(R.string.settings_value_unknown),
                    clickable = false,
                    trailingContent = {
                        ExpressiveIconButton(
                            icon = if (apiKey.revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription =
                                stringResource(
                                    if (apiKey.revealed) R.string.server_settings_api_key_hide else R.string.server_settings_api_key_show,
                                ),
                            onClick = actions.onToggleReveal,
                        )
                    },
                ),
                ListItem(
                    icon = Icons.Filled.ContentCopy,
                    label = stringResource(R.string.server_settings_api_key_copy),
                    disabled = apiKey.key.isEmpty(),
                    onClick = { actions.onCopy(apiKey.key) },
                ),
                ListItem(
                    icon = Icons.Filled.Refresh,
                    iconTint = BingeSentiment.Negative.fill(),
                    label = stringResource(R.string.server_settings_api_key_regenerate),
                    detail = stringResource(R.string.server_settings_api_key_regenerate_detail),
                    loading = apiKey.regenerating,
                    onClick = onRegenerate,
                ),
            ),
    )
    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
}

/** "•••• abcd": enough of the key to tell two apart, or null before one has been read. */
@Composable
private fun ApiKeyState.hidden(): String? = key.maskedKey()

/** Any key as only its last characters, "•••• abcd"; null when there is none. */
@Composable
internal fun String.maskedKey(): String? =
    takeLast(KEY_TAIL).takeIf { it.isNotEmpty() }?.let { stringResource(R.string.server_settings_api_key_hidden, it) }
