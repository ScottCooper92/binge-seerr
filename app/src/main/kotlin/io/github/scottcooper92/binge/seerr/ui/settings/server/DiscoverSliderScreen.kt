package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NumberKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.VerbatimKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.multiChoiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * What the slider editor does beside the form: change the kind, pick what it queries (a keyword, a genre, a studio, a
 * network's id, a streaming region and its providers), and delete.
 */
class SliderEditorActions(
    val onSelectType: (SliderType) -> Unit,
    val onToggleKeyword: (Int) -> Unit,
    val onSelectGenre: (Int) -> Unit,
    val onSearchKeywords: (String) -> Unit,
    val onLoadKeywordNames: (List<Int>) -> Unit,
    val onSearchStudios: (String) -> Unit,
    val onSelectStudio: (Company) -> Unit,
    val onNameNetwork: (Int) -> Unit,
    val onLoadRegions: () -> Unit,
    val onSelectRegion: (String) -> Unit,
    val onToggleProvider: (Int) -> Unit,
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
                ) + dataItems(draft, extras, enabled, actions, sliderActions),
        )
        if (draft.id != null) DeleteGroup(sliderActions.onDelete)
    }
}

/** The rows for what the kind queries: a keyword search, a genre list, a studio search, a network id, a region and its providers, or the typed value. */
@Composable
private fun dataItems(
    draft: SliderForm,
    extras: SliderExtras,
    enabled: Boolean,
    actions: EditorActions<SliderForm>,
    sliderActions: SliderEditorActions,
): List<ListItem> =
    when (draft.type.dataKind) {
        SliderDataKind.Keywords ->
            listOf(
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
                ),
            )
        SliderDataKind.Genre -> listOf(genreItem(draft, extras.genres, enabled, actions, sliderActions.onSelectGenre))
        SliderDataKind.Studio -> listOf(studioItem(draft, extras.studios, enabled, sliderActions))
        SliderDataKind.Network -> listOf(networkItem(draft, extras.networkNames, enabled, actions, sliderActions.onNameNetwork))
        SliderDataKind.Streaming -> streamingItems(draft, extras, enabled, sliderActions)
        SliderDataKind.Text ->
            listOf(
                textSettingItem(
                    icon = Icons.Filled.Search,
                    label = stringResource(R.string.server_settings_slider_data),
                    value = draft.data,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(data = value) } },
                    hint = stringResource(draft.type.dataHintRes()),
                    required = true,
                    keyboard = VerbatimKeyboard,
                ),
            )
    }

/** The studio, picked from TMDB's company search and named on the row; an id the server holds is named once it is read. */
@Composable
private fun studioItem(
    draft: SliderForm,
    studios: CompanySearch,
    enabled: Boolean,
    sliderActions: SliderEditorActions,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    val title = stringResource(R.string.server_settings_slider_studio_row)
    val id = draft.data.trim().toIntOrNull()
    if (open) {
        CompanyPickerDialog(
            title = title,
            chosen = id,
            search = studios,
            onSearch = sliderActions.onSearchStudios,
            onPick = { company ->
                sliderActions.onSelectStudio(company)
                open = false
            },
            onDismiss = { open = false },
        )
    }
    return ListItem(
        icon = Icons.Filled.Business,
        label = title,
        detail = id?.let { studios.names[it] ?: it.toString() } ?: draft.data.ifBlank { stringResource(R.string.settings_value_not_set) },
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}

/** TMDB has no network search, so the id is typed; the row names the network it is once the server has said. */
@Composable
private fun networkItem(
    draft: SliderForm,
    names: Map<Int, String>,
    enabled: Boolean,
    actions: EditorActions<SliderForm>,
    onName: (Int) -> Unit,
): ListItem {
    val id = draft.data.trim().toIntOrNull()
    LaunchedEffect(id) { id?.let(onName) }
    val invalid = stringResource(R.string.server_settings_slider_network_invalid)
    return textSettingItem(
        icon = Icons.Filled.Tv,
        label = stringResource(R.string.server_settings_slider_network_row),
        value = draft.data,
        enabled = enabled,
        onChange = { value -> actions.onEdit { it.copy(data = value) } },
        hint = stringResource(R.string.server_settings_slider_data_company),
        required = true,
        check = { typed -> invalid.takeIf { typed.isNotBlank() && typed.trim().toIntOrNull() == null } },
        shown =
            id?.let { names[it]?.let { name -> "$name ($it)" } ?: it.toString() }
                ?: draft.data.ifBlank { stringResource(R.string.settings_value_not_set) },
        keyboard = NumberKeyboard,
    )
}

/** The region first, then the providers TMDB lists there for the kind's movies or TV; the providers wait for a region. */
@Composable
private fun streamingItems(
    draft: SliderForm,
    extras: SliderExtras,
    enabled: Boolean,
    sliderActions: SliderEditorActions,
): List<ListItem> {
    val pick = draft.data.toStreamingPick()
    val providersTitle = stringResource(R.string.server_settings_slider_providers)
    val notSet = stringResource(R.string.settings_value_not_set)
    val region =
        regionSettingItem(
            icon = Icons.Filled.LiveTv,
            title = stringResource(R.string.server_settings_streaming_region),
            value = pick.region,
            choices = extras.regions,
            enabled = enabled,
            onOpen = sliderActions.onLoadRegions,
            onSelect = sliderActions.onSelectRegion,
        ).let { if (pick.region.isBlank()) it.copy(detail = notSet) else it }
    val providers =
        when (val choices = extras.providers) {
            is ProviderChoices.Ready ->
                multiChoiceSettingItem(
                    icon = Icons.Filled.Subscriptions,
                    title = providersTitle,
                    choices = choices.providers.map { BingeChoice(it.id, it.label, icon = Icons.Filled.Subscriptions) },
                    selected = pick.providerIds.toSet(),
                    enabled = enabled,
                    emptyLabel = notSet,
                    onToggle = sliderActions.onToggleProvider,
                )
            ProviderChoices.Failed ->
                untestedItem(
                    Icons.Filled.Subscriptions,
                    providersTitle,
                    waitingFor = stringResource(R.string.server_settings_slider_providers_failed),
                )
            ProviderChoices.Loading ->
                untestedItem(
                    Icons.Filled.Subscriptions,
                    providersTitle,
                    savedLabel = pick.providerIds.joinToString(", ").ifBlank { null },
                    waitingFor = stringResource(R.string.tv_loading),
                ).copy(loading = true)
            ProviderChoices.Idle ->
                untestedItem(
                    Icons.Filled.Subscriptions,
                    providersTitle,
                    savedLabel = pick.providerIds.joinToString(", ").ifBlank { null },
                    waitingFor = stringResource(R.string.server_settings_slider_pick_region),
                )
        }
    return listOf(region, providers)
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
