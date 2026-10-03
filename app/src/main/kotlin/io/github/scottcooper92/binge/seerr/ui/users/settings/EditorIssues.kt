package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.annotation.StringRes
import io.github.scottcooper92.binge.seerr.R

/** Why a field blocks a save: nothing is there yet ([Missing]), or what is there cannot be used ([Invalid]). */
internal enum class EditorIssueKind { Missing, Invalid }

/**
 * One reason a draft cannot be saved, placed by [section] and [field] ids the page's own composables
 * tag with. A page's validator lists them in the order the sections read, so the first is the one a
 * failed Save takes the user to.
 */
internal data class EditorIssue(
    val section: String,
    val field: String,
    val kind: EditorIssueKind,
    @StringRes val messageRes: Int,
)

internal fun missing(
    section: String,
    field: String,
    @StringRes messageRes: Int = R.string.editor_field_required,
) = EditorIssue(section, field, EditorIssueKind.Missing, messageRes)

internal fun invalid(
    section: String,
    field: String,
    @StringRes messageRes: Int,
) = EditorIssue(section, field, EditorIssueKind.Invalid, messageRes)

/**
 * What a page shows beside its fields. A [EditorIssueKind.Invalid] value is wrong the moment it is
 * typed, so it is shown at once; a [EditorIssueKind.Missing] one is not, or a new form would open
 * red before the user has done anything, and is held back until a Save has been tried.
 */
internal fun List<EditorIssue>.visible(submitted: Boolean): List<EditorIssue> =
    if (submitted) {
        this
    } else {
        filter {
            it.kind ==
                EditorIssueKind.Invalid
        }
    }

/**
 * Whether a section's body is open. The user's own choice wins over [defaultExpanded], except that a
 * section with something to fix is never closed, so an error cannot be collapsed out of sight.
 */
internal fun sectionExpanded(
    userChoice: Boolean?,
    defaultExpanded: Boolean,
    issueCount: Int,
): Boolean = issueCount > 0 || (userChoice ?: defaultExpanded)

/** A page's rules: the id its sections' saved state is keyed under, and the draft's [issues]. */
internal class EditorValidation<T>(
    val formKey: String,
    val issues: (T) -> List<EditorIssue>,
)
