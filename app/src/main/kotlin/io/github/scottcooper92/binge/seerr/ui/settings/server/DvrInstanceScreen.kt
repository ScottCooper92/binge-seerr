package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSection
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorValidation
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * One instance, as collapsible sections (#549): the connection and a test that reaches it, the
 * destination picked from what it answered, the anime destination of a Sonarr, and an advanced group
 * of the optional address fields and the flags. A section holding a required field starts open.
 * Delete asks first; a default instance gone is every plain request with nowhere to go.
 */
@Composable
fun DvrInstanceScreen(
    state: ExtrasEditorUiState<DvrForm, DvrExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<DvrForm>,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val ready = state as? ExtrasEditorUiState.Ready<DvrForm, DvrExtras>
    val extras = ready?.extras ?: DvrExtras()
    val choicesLoaded = extras.choices != null
    val validation = remember(choicesLoaded) { EditorValidation<DvrForm>(DVR_FORM_KEY) { it.issues(choicesLoaded) } }
    val title = ready?.draft?.let { if (it.id == null) null else it.name.ifBlank { it.type.name } }
    EditorPage(
        title = title ?: stringResource(R.string.server_settings_dvr_new),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
        validation = validation,
    ) { draft, enabled ->
        EditorSection(DvrSections.CONNECTION, stringResource(R.string.settings_group_connection)) {
            ConnectionFields(draft, enabled, actions)
            BingeOutlinedButton(
                label = stringResource(R.string.server_settings_dvr_test),
                onClick = onTest,
                enabled = enabled && draft.connectionValid && !extras.testing,
                loading = extras.testing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DestinationFields(draft, extras.choices, enabled, actions)
        AdvancedFields(draft, enabled, actions)
        if (draft.id != null) DeleteButton(onDelete)
    }
}

@Composable
private fun ConnectionFields(
    draft: DvrForm,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    EditorTextField(
        draft.name,
        stringResource(R.string.server_settings_dvr_name),
        icon = Icons.Filled.Badge,
        enabled = enabled,
        prose = true,
        fieldId = DvrFields.NAME,
        required = true,
    ) { value ->
        actions.onEdit { it.copy(name = value) }
    }
    EditorTextField(
        draft.host,
        stringResource(R.string.server_settings_host),
        icon = Icons.Filled.Dns,
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        placeholder = stringResource(R.string.placeholder_host),
        fieldId = DvrFields.HOST,
        required = true,
    ) { value ->
        actions.onEdit { it.copy(host = value) }
    }
    EditorTextField(
        draft.port,
        stringResource(R.string.server_settings_port),
        icon = Icons.Filled.Tag,
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        fieldId = DvrFields.PORT,
        required = true,
    ) { value -> actions.onEdit { it.copy(port = value) } }
    EditorToggleRow(
        editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled) { value ->
            actions.onEdit { it.copy(useSsl = value) }
        },
    )
    EditorTextField(
        draft.apiKey,
        stringResource(R.string.server_settings_api_key),
        icon = Icons.Filled.Key,
        enabled = enabled,
        secret = true,
        fieldId = DvrFields.API_KEY,
        required = true,
    ) { value ->
        actions.onEdit { it.copy(apiKey = value) }
    }
}

@Composable
internal fun TagChips(
    tags: List<io.github.scottcooper92.binge.seerr.ui.Choice>,
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    if (tags.isEmpty()) return
    Text(stringResource(R.string.request_tags), style = MaterialTheme.typography.titleSmall)
    tags.forEach { tag ->
        FilterChip(selected = tag.id in selected, onClick = { onToggle(tag.id) }, enabled = enabled, label = { Text(tag.label) })
    }
}

@Composable
internal fun DeleteButton(onDelete: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    BingeOutlinedButton(
        label = stringResource(R.string.server_settings_delete),
        onClick = { confirming = true },
        destructive = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.server_settings_delete_title),
            message = stringResource(R.string.server_settings_delete_message),
            confirmLabel = stringResource(R.string.server_settings_delete),
            destructive = true,
            onConfirm = {
                confirming = false
                onDelete()
            },
            onDismiss = { confirming = false },
        )
    }
}

internal fun Set<Int>.toggled(id: Int): Set<Int> = if (id in this) this - id else this + id
