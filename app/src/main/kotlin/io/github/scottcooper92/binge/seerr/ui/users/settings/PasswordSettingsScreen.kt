package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/**
 * The password page, as the web client words it: which sign-in the password is for, a note when the account has none
 * yet, then the current one where the server wants it and the new one twice. The fields stay inline rather than in
 * sheets, so a password manager sees them as one sign-in form and can fill or save it.
 */
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
        val mismatch = draft.confirm.isNotEmpty() && draft.confirm != draft.new
        ItemGroup(
            title = null,
            rows = emptyList(),
            belowRows = {
                GroupMessage(stringResource(R.string.user_settings_password_description), error = false)
                if (!draft.hasPassword) GroupMessage(stringResource(R.string.user_settings_password_none), error = false)
                PasswordFields(draft, enabled, mismatch, actions.onEdit)
                if (mismatch) GroupMessage(stringResource(R.string.user_settings_password_mismatch), error = true)
            },
        )
    }
}

@Composable
private fun PasswordFields(
    draft: PasswordSettings,
    enabled: Boolean,
    mismatch: Boolean,
    onEdit: ((PasswordSettings) -> PasswordSettings) -> Unit,
) {
    Column(
        modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        if (draft.currentRequired) {
            EditorTextField(
                draft.current,
                stringResource(R.string.user_settings_password_current),
                enabled = enabled,
                secret = true,
                contentType = ContentType.Password,
            ) { value ->
                onEdit { it.copy(current = value) }
            }
        }
        // NewPassword on both halves of the pair: it is what tells a password manager to offer a
        // generated one and then to update the entry it already holds rather than add a second.
        EditorTextField(
            draft.new,
            stringResource(R.string.user_settings_password_new),
            enabled = enabled,
            secret = true,
            supporting = stringResource(R.string.user_settings_password_hint, PasswordSettings.MIN_PASSWORD_LENGTH),
            contentType = ContentType.NewPassword,
        ) { value -> onEdit { it.copy(new = value) } }
        EditorTextField(
            draft.confirm,
            stringResource(R.string.user_settings_password_confirm),
            enabled = enabled,
            secret = true,
            isError = mismatch,
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Done,
        ) { value -> onEdit { it.copy(confirm = value) } }
    }
}
