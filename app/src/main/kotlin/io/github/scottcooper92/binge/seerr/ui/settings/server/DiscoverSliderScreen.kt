package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * A custom slider's page: its kind, its title, and the data the kind queries — ids, a code or a
 * search, typed as the web client stores them, with the kind's own hint beneath.
 */
@Composable
fun DiscoverSliderScreen(
    state: EditorUiState<SliderForm>,
    events: Flow<EditorEvent>,
    actions: EditorActions<SliderForm>,
    onDelete: () -> Unit,
) {
    EditorPage(
        title =
            stringResource(
                if ((state as? EditorUiState.Ready)?.draft?.id ==
                    null
                ) {
                    R.string.server_settings_slider_add
                } else {
                    R.string.server_settings_slider_edit
                },
            ),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ChoicePicker(
            title = stringResource(R.string.server_settings_slider_type),
            choices = SliderType.customTypes.map { it to stringResource(it.labelRes()) },
            selected = draft.type,
            onSelect = { type -> actions.onEdit { it.copy(type = type) } },
        )
        EditorTextField(draft.title, stringResource(R.string.server_settings_slider_title), enabled = enabled) { value ->
            actions.onEdit { it.copy(title = value) }
        }
        EditorTextField(
            draft.data,
            stringResource(R.string.server_settings_slider_data),
            enabled = enabled,
            supporting = stringResource(draft.type.dataHintRes()),
        ) { value -> actions.onEdit { it.copy(data = value) } }
        if (draft.id != null) DeleteButton(onDelete)
    }
}
