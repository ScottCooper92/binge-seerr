package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

/** What every editor page hands its screen: leave, retry the load, change the draft, and save it. */
class EditorActions<T>(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onEdit: ((T) -> T) -> Unit,
    val onSave: () -> Unit,
)

/**
 * The frame every per-user settings page shares: the title, a Save action live only while the
 * draft differs from the record and passes [canSave], and a snackbar for the outcome. [scrolling]
 * is off for a body that scrolls itself.
 */
@Composable
internal fun <T> EditorPage(
    title: String,
    state: EditorUiState<T>,
    events: Flow<EditorEvent>,
    actions: EditorActions<T>,
    canSave: (T) -> Boolean = { true },
    showSaveAction: Boolean = true,
    scrolling: Boolean = true,
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                EditorEvent.Saved ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.user_settings_saved), SnackbarMessageKind.Confirmation)
                is EditorEvent.Failed ->
                    snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()), SnackbarMessageKind.Error)
                is EditorEvent.Notice ->
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes), SnackbarMessageKind.Confirmation)
            }
        }
    }
    val ready = state as? EditorUiState.Ready<T>
    Scaffold(
        snackbarHost = { BingeSnackbarHost(snackbarHostState) },
        topBar = {
            BingeTopBar(
                title = title,
                onBack = actions.onBack,
                actions = {
                    if (showSaveAction && ready != null) {
                        BingeTextButton(
                            label = stringResource(R.string.user_settings_save),
                            onClick = actions.onSave,
                            enabled = ready.dirty && !ready.saving && canSave(ready.draft),
                            loading = ready.saving,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                EditorUiState.Loading -> LoadingScreen()
                is EditorUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
                is EditorUiState.Ready ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(if (scrolling) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                .padding(dimensionResource(DesR.dimen.screen_content_inset)),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
                    ) {
                        content(state.draft, !state.saving)
                    }
            }
        }
    }
}

@Composable
internal fun EditorTextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    isError: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun EditorSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun EditorSectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_s)),
    )
}
