package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.DISABLED_ALPHA
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.paneBackOrNull
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.FormAction
import com.binge.designsystem.template.FormActionPlacement
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenFooter
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.state.DialogMessage
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.MessageDialogHost
import com.cooper.binge.companion.seerr.ui.state.SettingsRowsSkeleton
import com.cooper.binge.companion.seerr.ui.state.dialogMessage
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.needsDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.cooper.binge.companion.seerr.core.ui.R as UiR

/**
 * The top-bar/bottom-bar insets a `scrolling = false` [EditorPage] does not apply itself. A `scrolling = true`
 * page folds them into its own [androidx.compose.foundation.verticalScroll], so content scrolls under both bars.
 * A page that scrolls itself must do the same, as its scrollable's content padding rather than a [Modifier.padding]
 * around it, or its content stops at the bars' edge. Defaults to zero, so a page that reads it without checking
 * `scrolling` gets no inset rather than an exception.
 */
val LocalEditorPageInsets = compositionLocalOf { PaddingValues() }

/**
 * A page's one primary action, anchored in a `bottomBar` slot rather than scrolling away: Discover Sliders' Add,
 * a Permissions page's Save. It is a [ScreenFooter] holding a filled button: the design system's raised bar, which clears
 * the navigation bar and keeps the content's side inset. Named for [EditorPage.bottomBar], but any screen with a `bottomBar`
 * slot of the same shape can use it.
 */
@Composable
fun EditorPageActionBar(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    ScreenFooter(modifier = modifier) {
        BingeFilledButton(
            label = label,
            onClick = onClick,
            enabled = enabled,
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What every editor page hands its screen: leave, retry the load, change the draft, and save it. */
class EditorActions<T>(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onEdit: ((T) -> T) -> Unit,
    val onSave: () -> Unit,
    /** Where the page goes once its record is deleted: the record is gone, so there is nothing left to show. */
    val onDeleted: () -> Unit = {},
    /** Where a create form goes once the server has accepted it. */
    val onCommitted: () -> Unit = {},
)

/** Every editor route wires the same four: only where Back goes is the page's own. */
fun <T> CommitEditorViewModel<T>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/**
 * Same as above, for a page that reads the shared library's commit form directly. [leaveOnDeleted] and [leaveOnCommit] say the
 * page leaves, through [onBack], once its record is deleted, or once a create form is accepted.
 */
fun <T, X> CommitFormViewModel<T, X>.editorActions(
    onBack: () -> Unit,
    leaveOnDeleted: Boolean = false,
    leaveOnCommit: Boolean = false,
): EditorActions<T> =
    EditorActions(
        onBack = onBack,
        onRetry = ::reload,
        onEdit = ::edit,
        onSave = ::save,
        onDeleted = if (leaveOnDeleted) onBack else ({}),
        onCommitted = if (leaveOnCommit) onBack else ({}),
    )

/** Same as above, for a page that saves as it changes and reads the shared library's editor directly. */
fun <T, X> SettingsEditorViewModel<T, X>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/** Same as above, for a page that saves as it changes on the shared library's engine. */
fun <T> SaveAsMadeEditorViewModel<T>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/** Same as above, for a page that saves as it changes and renders extras beside its draft, on the shared library's engine. */
fun <T, X> SaveAsMadeEditorWithExtrasViewModel<T, X>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/** Same as above, for a page with an explicit commit that renders extras beside its draft. */
fun <T, X> CommitEditorWithExtrasViewModel<T, X>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/** Same as above, for a record that is new (committed) or existing (saved as it changes), with extras beside its draft. */
fun <T, X> DualModeEditorViewModel<T, X>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)

/**
 * The frame every per-user settings page shares: the title, a Save action live only while the draft differs from the
 * record and passes [canSave], and a snackbar for the outcome.
 *
 * Leaving with a change unsaved, by the top bar's Back, the system Back or Cancel, asks first ([DiscardChangesDialog]).
 * A page that saves as it changes asks only while a change is held: a change that leaves a valid record failing
 * [canSave] is not sent (#957), so leaving would drop it (#1447). A record the server sent already failing [canSave]
 * goes back as it came, so it holds nothing.
 *
 * @param scrolling off for a body that scrolls itself.
 * @param extraActions composes into the same top-bar row as Save, after it: an overflow menu button, say.
 * @param bottomBar the page's own, passed straight through to [BingeScreenScaffold].
 * @param footerCommit puts the commit in that same bottom bar, Cancel beside it, for a page whose commit stays explicit
 *   because the server checks what was entered or the fields only make sense together (#941).
 * @param commitLabel names the commit where Save is not the word: Create for something new, Change password.
 */
@Composable
fun <T> EditorPage(
    title: String,
    state: EditorUiState<T>,
    events: Flow<EditorEvent>,
    actions: EditorActions<T>,
    canSave: (T) -> Boolean = { true },
    showSaveAction: Boolean = true,
    saveAsMade: Boolean = false,
    footerCommit: Boolean = false,
    commitLabel: String? = null,
    scrolling: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorEventSnackbarEffect(events, snackbarHostState)
    EditorFrame(
        title = title,
        state =
            when (state) {
                EditorUiState.Loading -> FrameState.Loading
                is EditorUiState.Error -> FrameState.Error(state.error)
                is EditorUiState.Ready ->
                    FrameState.Ready(state.draft, state.saved, state.saving, state.saveStatus, state.saveFailed)
            },
        snackbarHostState = snackbarHostState,
        actions = actions,
        canSave = canSave,
        options = FrameOptions(showSaveAction, saveAsMade, footerCommit, commitLabel, scrolling),
        bottomBar = bottomBar,
        extraActions = extraActions,
        content = content,
    )
}

/** How [EditorFrame] lays out and saves, as [EditorPage] documents each. */
internal data class FrameOptions(
    val showSaveAction: Boolean,
    val saveAsMade: Boolean,
    val footerCommit: Boolean,
    val commitLabel: String?,
    val scrolling: Boolean,
)

/** What [EditorFrame] draws from: the one shape every editor's state is read into, whichever library state it came from. */
internal sealed interface FrameState<out T> {
    data object Loading : FrameState<Nothing>

    data class Error(
        val error: SeerrError,
    ) : FrameState<Nothing>

    data class Ready<T>(
        val draft: T,
        val saved: T,
        val saving: Boolean = false,
        val saveStatus: SaveStatus = SaveStatus.Idle,
        val saveFailed: Boolean = false,
    ) : FrameState<T> {
        val dirty: Boolean get() = draft != saved
    }
}

/** The frame every editor page shares, under [EditorPage] and [CommitEditorPage]: the bars, the actions, the guard on leaving, and the body. */
@Composable
internal fun <T> EditorFrame(
    title: String,
    state: FrameState<T>,
    snackbarHostState: SnackbarHostState,
    actions: EditorActions<T>,
    canSave: (T) -> Boolean,
    options: FrameOptions,
    bottomBar: @Composable () -> Unit,
    extraActions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val showSaveAction = options.showSaveAction
    val saveAsMade = options.saveAsMade
    val footerCommit = options.footerCommit
    val commitLabel = options.commitLabel
    val scrolling = options.scrolling
    val ready = state as? FrameState.Ready<T>
    val guarded =
        when {
            ready == null || !ready.dirty -> false
            saveAsMade -> !canSave(ready.draft) && canSave(ready.saved)
            else -> !ready.saving
        }
    val onBack = rememberDiscardGuard(guarded = guarded, onLeave = actions.onBack)
    FormScreen(
        title = title,
        onBack = paneBackOrNull(onBack),
        bar = ScreenBar.Small,
        snackbarHostState = snackbarHostState,
        placement = if (footerCommit) FormActionPlacement.Footer else FormActionPlacement.TopBar,
        primaryAction =
            ready?.let { draft ->
                when {
                    // A page that saves as it changes has nothing to press.
                    footerCommit || (showSaveAction && !saveAsMade) ->
                        FormAction(
                            label = commitLabel ?: stringResource(R.string.user_settings_save),
                            onClick = actions.onSave,
                            enabled = draft.dirty && !draft.saving && canSave(draft.draft),
                            busy = draft.saving,
                        )
                    else -> null
                }
            },
        secondaryAction =
            ready?.takeIf { footerCommit }?.let { draft ->
                FormAction(label = stringResource(R.string.editor_cancel), onClick = onBack, enabled = !draft.saving)
            },
        scrolling = scrolling,
        saveStatus = if (saveAsMade) ready?.let { formSaveStatus(it.saveStatus, it.saveFailed, actions.onSave) } else null,
        extraActions = extraActions,
        bottomBar = bottomBar,
        notReady =
            when (state) {
                FrameState.Loading -> { inner -> SettingsRowsSkeleton(Modifier.padding(inner)) }
                is FrameState.Error -> { inner ->
                    ErrorScreen(error = state.error, modifier = Modifier.padding(inner), onRetry = actions.onRetry)
                }
                is FrameState.Ready -> null
            },
    ) { inner ->
        // A page that scrolls itself folds [inner] into its own scrollable through LocalEditorPageInsets; a page
        // the template scrolls is handed none, since the scroll already carries it.
        ready?.let { draft ->
            CompositionLocalProvider(LocalEditorPageInsets provides inner) {
                content(draft.draft, !draft.saving)
            }
        }
    }
}

/**
 * Back for a page that may hold an unsaved change: while [guarded], Back (the system's too) asks before [onLeave] runs, and
 * otherwise goes straight through. The question defaults to staying: dismissing it keeps editing.
 */
@Composable
private fun rememberDiscardGuard(
    guarded: Boolean,
    onLeave: () -> Unit,
): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = guarded) { asking = true }
    if (asking && guarded) {
        DiscardChangesDialog(
            onDiscard = {
                asking = false
                onLeave()
            },
            onKeepEditing = { asking = false },
        )
    }
    return { if (guarded) asking = true else onLeave() }
}

/**
 * Each editor outcome as a snackbar; a newer one supersedes the one still showing. A refusal the server explained, a rejected
 * sign-in and a blocked local network are dialogs: the user must read them to act.
 */
@Composable
fun EditorEventSnackbarEffect(
    events: Flow<EditorEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    var dialog by remember { mutableStateOf<DialogMessage?>(null) }
    MessageDialogHost(dialog) { dialog = null }
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            if (event is EditorEvent.TestFailed) {
                dialog =
                    DialogMessage(
                        resources.getString(R.string.server_settings_agent_test_failed_title),
                        resources.getString(R.string.server_settings_agent_test_failed_body, event.reason),
                    )
                return@collectLatest
            }
            if (event is EditorEvent.Failed) {
                val detail = event.detail
                if (detail != null) {
                    dialog =
                        DialogMessage(
                            resources.getString(UiR.string.state_error_rejected_title),
                            resources.getString(R.string.state_error_rejected_detail, detail),
                        )
                    return@collectLatest
                }
                if (event.error.needsDialog()) {
                    dialog = event.error.dialogMessage(resources)
                    return@collectLatest
                }
            }
            when (event) {
                EditorEvent.Saved ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.user_settings_saved), SnackbarMessageKind.Confirmation)
                is EditorEvent.Failed ->
                    snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()), SnackbarMessageKind.Error)
                is EditorEvent.TestFailed -> Unit
                is EditorEvent.Notice ->
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes), SnackbarMessageKind.Confirmation)
                is EditorEvent.Undoable -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            resources.getString(event.messageRes, resources.getString(event.argRes)),
                            SnackbarMessageKind.Confirmation,
                            actionLabel = resources.getString(R.string.user_settings_undo),
                        )
                    if (result == SnackbarResult.ActionPerformed) event.undo()
                }
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
fun rowLabelColor(
    enabled: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurface,
): Color = if (enabled) color else color.copy(alpha = DISABLED_ALPHA)
