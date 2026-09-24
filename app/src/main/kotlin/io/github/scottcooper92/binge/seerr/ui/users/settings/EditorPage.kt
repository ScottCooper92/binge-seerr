package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

/** M3's disabled content alpha, which it exposes no token for. */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * The top-bar/bottom-bar insets a `scrolling = false` [EditorPage] does not itself apply. A
 * `scrolling = true` page folds them into its own [androidx.compose.foundation.verticalScroll], so
 * its content scrolls fully under both transparent bars the way every other screen's does; a page
 * that scrolls itself has to fold them into its *own* scrollable the same way - as that scrollable's
 * content padding, not a [Modifier.padding] wrapping it, or its content can only scroll up to the
 * bars' edge rather than under it. Defaults to zero, so a page that reads it without checking
 * `scrolling` first degrades to no inset rather than an exception.
 */
internal val LocalEditorPageInsets = compositionLocalOf { PaddingValues() }

/**
 * A page's one primary action, anchored in a `bottomBar` slot rather than scrolling away with the
 * rest of the content - Discover Sliders' Add, a Permissions page's Save, and the Advanced Request
 * hand-off's Request button all use this shape of [BingeActionFooter]: rounded and raised, since a
 * `bottomBar` sits outside the Scaffold's own body and needs its background to extend full-bleed
 * behind the gesture nav bar, which [BingeActionFooter.clearsNavigationBar] leaves to the button
 * alone to clear. Named for [EditorPage.bottomBar], its first call sites, but not tied to
 * [EditorUiState] - any screen with a `bottomBar` slot of the same shape can reach for it.
 */
@Composable
internal fun EditorPageActionBar(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    BingeActionFooter(
        label = label,
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        modifier = modifier,
        shape = BingeShapes.HeroTop,
        shadowElevation = dimensionResource(DesR.dimen.snackbar_elevation),
        bottomPadding = dimensionResource(DesR.dimen.padding_m),
        horizontalPadding = resolvedContentInset(),
        clearsNavigationBar = true,
    )
}

/** What every editor page hands its screen: leave, retry the load, change the draft, and save it. */
class EditorActions<T>(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onEdit: ((T) -> T) -> Unit,
    val onSave: () -> Unit,
)

/** Every editor route wires the same four: only where Back goes is the page's own. */
fun <T> EditorViewModel<T>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/** Same as above, for an editor whose page renders extras beside its draft. */
fun <T, X> ExtrasEditorViewModel<T, X>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/**
 * The frame every per-user settings page shares: the title, a Save action live only while the
 * draft differs from the record and passes [canSave], and a snackbar for the outcome. [scrolling]
 * is off for a body that scrolls itself.
 *
 * [extraActions] composes into the same top-bar row as Save, after it - an overflow menu button,
 * say. [bottomBar] is the page's own, passed straight through to [ScreenScaffold].
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
    bottomBar: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorEventSnackbarEffect(events, snackbarHostState)
    val ready = state as? EditorUiState.Ready<T>
    ScreenScaffold(
        title = title,
        onBack = actions.onBack,
        snackbarHostState = snackbarHostState,
        bottomBar = bottomBar,
        actions = {
            if (showSaveAction && ready != null) {
                BingeTextButton(
                    label = stringResource(R.string.user_settings_save),
                    onClick = actions.onSave,
                    enabled = ready.dirty && !ready.saving && canSave(ready.draft),
                    loading = ready.saving,
                )
            }
            extraActions()
        },
    ) { padding ->
        // The keyboard lifts the form rather than covering the field being typed in. The bars' insets are
        // consumed first, so the navigation bar under the keyboard is not counted twice.
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding()).consumeWindowInsets(padding)) {
            val inner = padding.innerPadding()
            when (state) {
                EditorUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is EditorUiState.Error -> ErrorScreen(error = state.error, modifier = Modifier.padding(inner), onRetry = actions.onRetry)
                is EditorUiState.Ready ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .imePadding()
                                // scrolling: folded into the scroll itself, so content scrolls fully
                                // under both bars like every other screen's. !scrolling: left for
                                // content to fold into its own scrollable via LocalEditorPageInsets -
                                // applied out here it would hard-clip that scrollable's viewport at
                                // the bars' edge rather than let it scroll under them.
                                .then(
                                    if (scrolling) {
                                        Modifier.verticalScroll(rememberScrollState()).padding(inner)
                                    } else {
                                        Modifier
                                    },
                                ).padding(resolvedContentInset()),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
                    ) {
                        CompositionLocalProvider(LocalEditorPageInsets provides inner) {
                            content(state.draft, !state.saving)
                        }
                    }
            }
        }
    }
}

/** Each editor outcome as a snackbar; a newer one supersedes the one still showing. */
@Composable
internal fun EditorEventSnackbarEffect(
    events: Flow<EditorEvent>,
    snackbarHostState: SnackbarHostState,
) {
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
                // The page is leaving on this one; a snackbar on a screen being popped is not seen.
                EditorEvent.Deleted -> Unit
            }
        }
    }
}

/**
 * [autoCorrect] is off for a value another system has to match exactly, such as a username.
 *
 * [contentType] is null by default because most of these fields hold a server's secret rather than
 * the user's own credential, and a credential provider should only be offered the latter.
 *
 * A [secret] field carries its own reveal toggle, so a long pasted key can be checked before it is
 * saved. It is not optional: a masked field with no way out of it is the thing being fixed.
 */
@Composable
internal fun EditorTextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoCorrect: Boolean = true,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    contentType: ContentType? = null,
    onValueChange: (String) -> Unit,
) {
    // remember rather than rememberSaveable: a field left revealed comes back masked after the app
    // is backgrounded, which is a small leak closed for no loss.
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon =
            if (secret) {
                { RevealToggle(revealed = revealed, enabled = enabled) { revealed = !revealed } }
            } else {
                null
            },
        visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else keyboardType,
                autoCorrectEnabled = autoCorrect,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .then(contentType?.let { type -> Modifier.semantics { this.contentType = type } } ?: Modifier),
    )
}

/**
 * The eye in a masked field's trailing slot. Its description names what the tap will do rather than
 * what the field holds, so a screen reader announces the action and the label is not read twice.
 *
 * It follows the field's own [enabled], which is off only while a save is in flight.
 */
@Composable
private fun RevealToggle(
    revealed: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle, enabled = enabled) {
        Icon(
            imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = stringResource(if (revealed) R.string.field_secret_hide else R.string.field_secret_show),
        )
    }
}

/**
 * A label beside a control that may be disabled. M3 dims the control and leaves text at full
 * strength, so a row reads as live while the thing it belongs to does not; there is no public token
 * for the 38% it dims to, which is where the constant comes from.
 */
@Composable
internal fun rowLabelColor(
    enabled: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurface,
): Color = if (enabled) color else color.copy(alpha = DISABLED_CONTENT_ALPHA)

@Composable
internal fun EditorSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
                // Inside the toggleable, so a row that insets itself keeps the whole inset tappable.
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = rowLabelColor(enabled),
            modifier = Modifier.weight(1f),
        )
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
