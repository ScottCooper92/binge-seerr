package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.BingeTextButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import com.binge.designsystem.R as DesR

class UserAdmissionActions(
    val onStart: () -> Unit,
    val onCancel: () -> Unit,
    val onStartCreate: () -> Unit,
    val onEditDraft: ((CreateUserDraft) -> CreateUserDraft) -> Unit,
    val onCreate: () -> Unit,
    val onStartImport: (UserOrigin) -> Unit,
    val onToggleCandidate: (String) -> Unit,
    val onSelectAllCandidates: (Boolean) -> Unit,
    val onImport: () -> Unit,
)

/** The add-user flow as sheets: the choice, then the form or the picker. */
@Composable
internal fun UserAdmissionSheets(
    state: UsersUiState.Ready,
    actions: UserAdmissionActions,
) {
    when (val admission = state.admission) {
        null -> Unit
        is UserAdmissionState.Choosing ->
            AdmissionChoiceSheet(importSource = state.importSource, actions = actions)
        is UserAdmissionState.Creating ->
            CreateUserSheet(draft = admission.draft, saving = admission.saving, actions = actions)
        is UserAdmissionState.Importing ->
            ImportUsersSheet(picker = admission.picker, saving = admission.saving, actions = actions)
    }
}

@Composable
private fun AdmissionChoiceSheet(
    importSource: UserOrigin?,
    actions: UserAdmissionActions,
) {
    BingeBottomSheet(onDismissRequest = actions.onCancel) {
        Column(modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
            ChoiceRow(
                icon = Icons.Filled.PersonAdd,
                label = stringResource(R.string.users_add_create),
                detail = stringResource(R.string.users_add_create_desc),
                onClick = actions.onStartCreate,
            )
            importSource?.let { source ->
                ChoiceRow(
                    icon = Icons.Filled.GroupAdd,
                    label = stringResource(R.string.users_add_import, stringResource(source.labelRes())),
                    detail = stringResource(R.string.users_add_import_desc),
                    onClick = { actions.onStartImport(source) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(onClick = onClick)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(icon, contentDescription = null)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A local account: email and username, and a password unless the server is asked to email one. */
@Composable
private fun CreateUserSheet(
    draft: CreateUserDraft,
    saving: Boolean,
    actions: UserAdmissionActions,
) {
    BingeBottomSheet(onDismissRequest = actions.onCancel, gesturesEnabled = !saving) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(stringResource(R.string.users_create_title), style = MaterialTheme.typography.titleLarge)
            Field(draft.email, stringResource(R.string.setup_email), enabled = !saving, keyboardType = KeyboardType.Email) { value ->
                actions.onEditDraft { it.copy(email = value) }
            }
            Field(draft.username, stringResource(R.string.setup_username), enabled = !saving) { value ->
                actions.onEditDraft { it.copy(username = value) }
            }
            if (!draft.generatePassword) {
                Field(
                    value = draft.password,
                    label = stringResource(R.string.setup_password),
                    enabled = !saving,
                    secret = true,
                    supporting = stringResource(R.string.user_settings_password_hint, CreateUserDraft.MIN_PASSWORD_LENGTH),
                ) { value -> actions.onEditDraft { it.copy(password = value) } }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = draft.generatePassword,
                            enabled = !saving && draft.canGeneratePassword,
                            role = Role.Checkbox,
                            onValueChange = { value -> actions.onEditDraft { it.copy(generatePassword = value) } },
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                Checkbox(checked = draft.generatePassword, onCheckedChange = null, enabled = !saving && draft.canGeneratePassword)
                Column {
                    Text(stringResource(R.string.users_create_generate), style = MaterialTheme.typography.bodyMedium)
                    if (!draft.canGeneratePassword) {
                        Text(
                            stringResource(R.string.users_create_generate_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            BingeSheetFooter(
                label = stringResource(R.string.users_create_submit),
                onClick = actions.onCreate,
                enabled = !saving && draft.valid,
                loading = saving,
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: String,
    enabled: Boolean,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The media server's accounts not yet on the server, each tickable, imported together. */
@Composable
private fun ImportUsersSheet(
    picker: ImportPicker,
    saving: Boolean,
    actions: UserAdmissionActions,
) {
    val candidates = picker.candidates
    BingeBottomSheet(onDismissRequest = actions.onCancel, gesturesEnabled = !saving) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = dimensionResource(DesR.dimen.padding_l)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.users_add_import, stringResource(picker.source.labelRes())),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!candidates.isNullOrEmpty()) {
                    val all = picker.selected.size == candidates.size
                    BingeTextButton(
                        label = stringResource(if (all) R.string.users_import_select_none else R.string.users_import_select_all),
                        onClick = { actions.onSelectAllCandidates(!all) },
                        enabled = !saving,
                    )
                }
            }
            when {
                picker.failed -> EmptyScreen(message = stringResource(R.string.users_import_failed))
                candidates == null -> BingeLoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                candidates.isEmpty() -> EmptyScreen(message = stringResource(R.string.users_import_empty))
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(candidates, key = { it.id }) { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                checked = candidate.id in picker.selected,
                                enabled = !saving,
                                onToggle = { actions.onToggleCandidate(candidate.id) },
                            )
                        }
                    }
                    val count = picker.selected.size
                    BingeSheetFooter(
                        label = pluralStringResource(R.plurals.users_import_submit, count, count),
                        onClick = actions.onImport,
                        enabled = !saving && count > 0,
                        loading = saving,
                        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ImportCandidate,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_xs)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        BingeInitialsAvatar(name = candidate.name, avatarUrl = candidate.avatarUrl, size = dimensionResource(DesR.dimen.avatar_size_md))
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
            candidate.email?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
