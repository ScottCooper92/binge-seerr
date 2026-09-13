package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import kotlinx.coroutines.flow.Flow

/** The metadata page: a provider for series and one for anime, and a test that reaches the ones the draft would use. */
@Composable
fun MetadataScreen(
    state: EditorUiState<MetadataForm>,
    extras: MetadataExtras,
    events: Flow<EditorEvent>,
    actions: EditorActions<MetadataForm>,
    onTest: () -> Unit,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_metadata),
        state = state,
        events = events,
        actions = actions,
    ) { draft, enabled ->
        val choices = MetadataProvider.entries.map { it to stringResource(it.labelRes()) }
        ChoicePicker(
            title = stringResource(R.string.server_settings_metadata_tv),
            choices = choices,
            selected = draft.tv,
            onSelect = { provider -> actions.onEdit { it.copy(tv = provider) } },
        )
        ChoicePicker(
            title = stringResource(R.string.server_settings_metadata_anime),
            choices = choices,
            selected = draft.anime,
            onSelect = { provider -> actions.onEdit { it.copy(anime = provider) } },
        )
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_metadata_test),
            onClick = onTest,
            enabled = enabled,
            loading = extras.testing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun MetadataProvider.labelRes(): Int =
    when (this) {
        MetadataProvider.Tmdb -> R.string.server_settings_metadata_tmdb
        MetadataProvider.Tvdb -> R.string.server_settings_metadata_tvdb
    }
