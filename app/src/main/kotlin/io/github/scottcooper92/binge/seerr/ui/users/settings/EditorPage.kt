package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.FormAction
import com.binge.designsystem.template.FormActionPlacement
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

/** M3's disabled content alpha, which it exposes no token for. */
internal const val DISABLED_CONTENT_ALPHA = 0.38f

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
 * say. [bottomBar] is the page's own, passed straight through to [BingeScreenScaffold].
 *
 * A page passes [validation] to opt into the sectioned form (#549): Cancel and Save move to a bar
 * pinned at the bottom, Save stays tappable while the draft has issues, and a Save that finds one
 * shows the page's required fields and scrolls the first issue into view (see [EditorSection]).
 * Without it the page is exactly as it was.
 */
@Composable
internal fun <T> EditorPage(
    title: String,
    state: EditorUiState<T>,
    events: Flow<EditorEvent>,
    actions: EditorActions<T>,
    canSave: (T) -> Boolean = { true },
    validation: EditorValidation<T>? = null,
    showSaveAction: Boolean = true,
    saveAsMade: Boolean = false,
    scrolling: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorEventSnackbarEffect(events, snackbarHostState)
    val ready = state as? EditorUiState.Ready<T>
    if (saveAsMade) SaveFailedSnackbar(ready?.saveFailed == true, snackbarHostState, actions.onSave)
    val form = rememberEditorFormState(validation?.formKey.orEmpty())
    val issues = remember(validation, ready?.draft) { ready?.draft?.let { validation?.issues?.invoke(it) }.orEmpty() }
    EditorRevealEffect(form)
    FormScreen(
        title = title,
        onBack = actions.onBack,
        snackbarHostState = snackbarHostState,
        placement = if (validation != null) FormActionPlacement.Footer else FormActionPlacement.TopBar,
        primaryAction =
            ready?.let { draft ->
                when {
                    validation != null ->
                        // Save stays tappable while the draft has issues: the tap is what shows the user which ones.
                        FormAction(
                            label = stringResource(R.string.user_settings_save),
                            onClick = { issues.firstOrNull()?.let(form::saveFailed) ?: actions.onSave() },
                            enabled = draft.dirty && !draft.saving,
                            busy = draft.saving,
                        )
                    // A page that saves as it changes has nothing to press.
                    showSaveAction && !saveAsMade ->
                        FormAction(
                            label = stringResource(R.string.user_settings_save),
                            onClick = actions.onSave,
                            enabled = draft.dirty && !draft.saving && canSave(draft.draft),
                            busy = draft.saving,
                        )
                    else -> null
                }
            },
        secondaryAction =
            ready?.takeIf { validation != null }?.let { draft ->
                FormAction(label = stringResource(R.string.editor_cancel), onClick = actions.onBack, enabled = !draft.saving)
            },
        scrolling = scrolling,
        extraActions = extraActions,
        bottomBar = bottomBar,
        notReady =
            when (state) {
                EditorUiState.Loading -> { inner -> LoadingScreen(Modifier.padding(inner)) }
                is EditorUiState.Error -> { inner ->
                    ErrorScreen(error = state.error, modifier = Modifier.padding(inner), onRetry = actions.onRetry)
                }
                is EditorUiState.Ready -> null
            },
    ) { inner ->
        // A page that scrolls itself folds [inner] into its own scrollable through LocalEditorPageInsets; a page
        // the template scrolls is handed none, since the scroll already carries it.
        ready?.let { draft ->
            CompositionLocalProvider(
                LocalEditorPageInsets provides inner,
                LocalEditorForm provides form.takeIf { validation != null },
                LocalEditorIssues provides issues.visible(form.submitted),
            ) {
                content(draft.draft, !draft.saving)
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
internal fun EditorSectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_s)),
    )
}

/**
 * A page that saves as it changes says a write failed in a snackbar that stays until it is answered: Retry sends the
 * draft again. It goes by itself once a later write starts, which carries the failed change with it.
 */
@Composable
private fun SaveFailedSnackbar(
    failed: Boolean,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
) {
    val message = stringResource(R.string.editor_save_failed)
    val retry = stringResource(R.string.action_try_again)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        // Shown again whenever it goes without the user answering it - an event's snackbar dismisses whatever is
        // showing - and after a retry, since a retry that fails at once leaves `failed` true and this effect unrestarted.
        // Leaving `failed` cancels the effect, and a cancelled snackbar clears itself.
        while (true) {
            val result = snackbarHostState.showSnackbar(message, actionLabel = retry, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) onRetry()
        }
    }
}
