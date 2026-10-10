package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.VerbatimKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/** What the slider editor does beside the form: change the kind, pick a keyword or a genre, and delete. */
class SliderEditorActions(
    val onSelectType: (SliderType) -> Unit,
    val onToggleKeyword: (Int) -> Unit,
    val onSelectGenre: (Int) -> Unit,
    val onSearchKeywords: (String) -> Unit,
    val onLoadKeywordNames: (List<Int>) -> Unit,
    val onDelete: () -> Unit,
)

/**
 * A custom slider's page as a group of list rows, as every other server editor is: its kind, its title, and the data the
 * kind queries. Keywords are picked by search and a genre by name, each saved as the ids the web client stores; the other
 * kinds are typed, with the kind's own hint.
 */
@Composable
fun DiscoverSliderScreen(
    state: ExtrasEditorUiState<SliderForm, SliderExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<SliderForm>,
    sliderActions: SliderEditorActions,
) {
    val ready = state as? ExtrasEditorUiState.Ready<SliderForm, SliderExtras>
    val extras = ready?.extras ?: SliderExtras()
    EditorPage(
        title =
            stringResource(
                if (ready?.draft?.id == null) R.string.server_settings_slider_add else R.string.server_settings_slider_edit,
            ),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
        footerCommit = true,
        commitLabel = ready?.takeIf { it.draft.id == null }?.let { stringResource(R.string.editor_create) },
    ) { draft, enabled ->
        ItemGroup(
            title = null,
            rows =
                listOf(
                    choiceSettingItem(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.server_settings_slider_type),
                        choices = SliderType.customTypes.map { it to stringResource(it.labelRes()) },
                        selected = draft.type,
                        enabled = enabled,
                        onSelect = sliderActions.onSelectType,
                    ),
                    textSettingItem(
                        icon = Icons.Filled.Title,
                        label = stringResource(R.string.server_settings_slider_title),
                        value = draft.title,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(title = value) } },
                        hint = stringResource(R.string.server_settings_slider_title_hint),
                        required = true,
                    ),
                    dataItem(draft, extras, enabled, actions, sliderActions),
                ),
        )
        if (draft.id != null) DeleteGroup(sliderActions.onDelete)
    }
}

/** The row for what the kind queries: a keyword search, a genre list, or the typed value. */
@Composable
private fun dataItem(
    draft: SliderForm,
    extras: SliderExtras,
    enabled: Boolean,
    actions: EditorActions<SliderForm>,
    sliderActions: SliderEditorActions,
): ListItem =
    when (draft.type.dataKind) {
        SliderDataKind.Keywords ->
            keywordPickerItem(
                icon = Icons.Filled.Key,
                title = stringResource(R.string.server_settings_rule_keywords),
                chosen = draft.data.tagIds(),
                search = extras.keywords,
                enabled = enabled,
                onSearch = sliderActions.onSearchKeywords,
                onToggle = sliderActions.onToggleKeyword,
                onLoadNames = sliderActions.onLoadKeywordNames,
                emptyLabel = stringResource(R.string.settings_value_not_set),
            )
        SliderDataKind.Genre -> genreItem(draft, extras.genres, enabled, actions, sliderActions.onSelectGenre)
        SliderDataKind.Text ->
            textSettingItem(
                icon = Icons.Filled.Search,
                label = stringResource(R.string.server_settings_slider_data),
                value = draft.data,
                enabled = enabled,
                onChange = { value -> actions.onEdit { it.copy(data = value) } },
                hint = stringResource(draft.type.dataHintRes()),
                required = true,
                keyboard = VerbatimKeyboard,
            )
    }

/**
 * One genre, picked by name from the kind's list; a saved id the list lacks stays as its number, and a server that cannot
 * send the list gets the typed id back, so the slider stays in reach.
 */
@Composable
private fun genreItem(
    draft: SliderForm,
    genres: GenreChoices,
    enabled: Boolean,
    actions: EditorActions<SliderForm>,
    onSelect: (Int) -> Unit,
): ListItem {
    val title = stringResource(R.string.server_settings_slider_genre)
    val saved = draft.data.trim().toIntOrNull()
    return when (genres) {
        is GenreChoices.Ready ->
            choiceSettingItem(
                icon = Icons.Filled.Category,
                title = title,
                choices = genreChecklist(genres.genres, setOfNotNull(saved)).map { it.id to it.label },
                selected = saved,
                enabled = enabled,
                onSelect = onSelect,
            )
        GenreChoices.Failed ->
            textSettingItem(
                icon = Icons.Filled.Category,
                label = title,
                value = draft.data,
                enabled = enabled,
                onChange = { value -> actions.onEdit { it.copy(data = value) } },
                hint = stringResource(R.string.server_settings_slider_data_genre),
                required = true,
                keyboard = VerbatimKeyboard,
            )
        GenreChoices.Loading ->
            untestedItem(Icons.Filled.Category, title, savedLabel = draft.data.ifBlank { null }).copy(loading = true)
    }
}
