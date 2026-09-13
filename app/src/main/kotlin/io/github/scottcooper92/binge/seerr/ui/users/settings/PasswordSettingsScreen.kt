package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import kotlinx.coroutines.flow.Flow

/** The password page: the current one where the server wants it, then the new one twice. */
@Composable
fun PasswordSettingsScreen(
    state: EditorUiState<PasswordSettings>,
    events: Flow<EditorEvent>,
    actions: EditorActions<PasswordSettings>,
) {
    EditorPage(
        title = stringResource(R.string.user_settings_page_password),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        if (!draft.hasPassword) {
            Text(stringResource(R.string.user_settings_password_none), style = MaterialTheme.typography.bodyMedium)
        }
        if (draft.currentRequired) {
            EditorTextField(
                draft.current,
                stringResource(R.string.user_settings_password_current),
                enabled = enabled,
                secret = true,
            ) { value ->
                actions.onEdit { it.copy(current = value) }
            }
        }
        EditorTextField(
            draft.new,
            stringResource(R.string.user_settings_password_new),
            enabled = enabled,
            secret = true,
            supporting = stringResource(R.string.user_settings_password_hint, PasswordSettings.MIN_PASSWORD_LENGTH),
        ) { value -> actions.onEdit { it.copy(new = value) } }
        val mismatch = draft.confirm.isNotEmpty() && draft.confirm != draft.new
        EditorTextField(
            draft.confirm,
            stringResource(R.string.user_settings_password_confirm),
            enabled = enabled,
            secret = true,
            supporting = stringResource(R.string.user_settings_password_mismatch).takeIf { mismatch },
            isError = mismatch,
        ) { value -> actions.onEdit { it.copy(confirm = value) } }
    }
}
