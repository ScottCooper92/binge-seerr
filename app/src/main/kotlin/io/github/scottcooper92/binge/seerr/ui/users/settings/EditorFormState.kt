package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus

/** What a failed Save asks the page to reveal; [token] makes a second failure on the same issue a new request. */
internal data class EditorReveal(
    val token: Int,
    val issue: EditorIssue,
)

internal class EditorFieldHandle(
    val bringIntoView: BringIntoViewRequester,
    val focus: FocusRequester?,
)

/**
 * What the sections and fields of one editor page share: whether Save has been tried, which fields
 * are on screen, and the one pending request to scroll an issue into view. Lives in [EditorPage],
 * which provides it through [LocalEditorForm]; a section or field outside a page finds none and
 * behaves as a plain, issue-free one.
 */
@Stable
internal class EditorFormState(
    val key: String,
    submitted: Boolean = false,
) {
    /** True once Save has been tried with issues outstanding, which is when required fields start to show. */
    var submitted by mutableStateOf(submitted)
        private set

    var reveal by mutableStateOf<EditorReveal?>(null)
        private set

    private val fields = mutableStateMapOf<String, EditorFieldHandle>()
    private var revealCount = 0

    /** Save was tried and [first] stopped it: show what is missing and take the user to it. */
    fun saveFailed(first: EditorIssue) {
        submitted = true
        reveal = EditorReveal(++revealCount, first)
    }

    internal fun register(
        id: String,
        handle: EditorFieldHandle,
    ) {
        fields[id] = handle
    }

    internal fun unregister(
        id: String,
        handle: EditorFieldHandle,
    ) {
        if (fields[id] === handle) fields.remove(id)
    }

    /**
     * Scrolls [request]'s field into view and, for a field that can take it, moves focus there. The two
     * frames wait for the section a failed Save just opened to compose its fields; a field the page did
     * not tag falls back to its section's header, so an issue is never a silent no-op.
     */
    suspend fun perform(request: EditorReveal) {
        withFrameNanos { }
        withFrameNanos { }
        val handle = fields[request.issue.field] ?: fields[sectionAnchor(request.issue.section)] ?: return
        handle.bringIntoView.bringIntoView()
        handle.focus?.let { restoreTvOverlayFocus(it) }
    }

    companion object {
        fun saver(key: String): Saver<EditorFormState, Boolean> = Saver(save = { it.submitted }, restore = { EditorFormState(key, it) })
    }
}

internal fun sectionAnchor(section: String) = "section:$section"

/** Survives rotation and process death, so a form that has shown its required fields keeps showing them. */
@Composable
internal fun rememberEditorFormState(key: String): EditorFormState =
    rememberSaveable(key, saver = EditorFormState.saver(key)) { EditorFormState(key) }

/** Carries out the form's pending reveal: one place, so every page's failed Save behaves the same. */
@Composable
internal fun EditorRevealEffect(form: EditorFormState) {
    LaunchedEffect(form.reveal) { form.reveal?.let { form.perform(it) } }
}

internal val LocalEditorForm = compositionLocalOf<EditorFormState?> { null }

/** The issues the page currently shows, already filtered by [visible]. */
internal val LocalEditorIssues = compositionLocalOf<List<EditorIssue>> { emptyList() }

/** The issue shown beside field [id], if any. */
@Composable
internal fun editorFieldIssue(id: String): EditorIssue? = LocalEditorIssues.current.firstOrNull { it.field == id }

/**
 * Tags a field or section header [id] so a failed Save can scroll to it. [takesFocus] is for a text
 * field; a picker row has nothing to focus and is only scrolled into view.
 */
@Composable
internal fun Modifier.editorField(
    id: String,
    takesFocus: Boolean = true,
): Modifier {
    val form = LocalEditorForm.current ?: return this
    val handle = remember(form, id) { EditorFieldHandle(BringIntoViewRequester(), if (takesFocus) FocusRequester() else null) }
    DisposableEffect(form, id, handle) {
        form.register(id, handle)
        onDispose { form.unregister(id, handle) }
    }
    return bringIntoViewRequester(handle.bringIntoView).let { tagged -> handle.focus?.let { tagged.focusRequester(it) } ?: tagged }
}
