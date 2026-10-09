package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * One collapsible group of an editor's fields, so a long form reads as a few small decisions (#549).
 *
 * The header is one button: it toggles the body, says whether it is open, and carries a count of the
 * fields that need attention. Whether it is open is remembered per editor and section, through
 * rotation and process death, and starts as [defaultExpanded] as it was when the section first appeared, until the user chooses. A section with
 * an issue to show (the page's [LocalEditorIssues]) is held open and its header is inert, so an error
 * cannot be collapsed out of sight. Put a section that holds a required field open by default; an
 * optional one can start closed.
 *
 * Lives beside [EditorPage] rather than in the design system because what makes it more than a
 * disclosure card is the page's protocol: the issues it reads, and the failed Save that opens it and
 * scrolls its field into view. Nothing else needs that yet, and it moves the day a second app does.
 *
 * The body is composed only while open. A closed body keeps nothing alive, and a field inside it has
 * no node for a D-pad to land on, which is the right traversal: header, then its fields, then the next
 * header.
 */
@Composable
internal fun EditorSection(
    sectionId: String,
    title: String,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val formKey = LocalEditorForm.current?.key.orEmpty()
    val issueCount = LocalEditorIssues.current.count { it.section == sectionId }
    var userChoice by rememberSaveable(formKey, sectionId, key = "editor-section:$formKey/$sectionId") { mutableStateOf<Boolean?>(null) }
    // Read once: a caller may pass a value that follows the draft, and a section the user is
    // working in must not close under their finger when it changes.
    val openedAs = rememberSaveable(formKey, sectionId, key = "editor-section-default:$formKey/$sectionId") { defaultExpanded }
    val expanded = sectionExpanded(userChoice, openedAs, issueCount)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BingeShapes.Large)
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        EditorSectionHeader(
            sectionId = sectionId,
            title = title,
            expanded = expanded,
            issueCount = issueCount,
            onToggle = { userChoice = !expanded },
        )
        if (expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                        .padding(bottom = dimensionResource(DesR.dimen.padding_m)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
                content = content,
            )
        }
    }
}

@Composable
private fun EditorSectionHeader(
    sectionId: String,
    title: String,
    expanded: Boolean,
    issueCount: Int,
    onToggle: () -> Unit,
) {
    val locked = issueCount > 0
    val state = stringResource(if (expanded) R.string.editor_section_expanded else R.string.editor_section_collapsed)
    val action = stringResource(if (expanded) R.string.editor_section_collapse_action else R.string.editor_section_expand_action)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .editorField(sectionAnchor(sectionId), takesFocus = false)
                .clickable(enabled = !locked, onClickLabel = action, role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = state
                }.defaultMinSize(minHeight = dimensionResource(R.dimen.editor_section_header_min_height))
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (locked) {
                Text(
                    pluralStringResource(R.plurals.editor_section_issues, issueCount, issueCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (!locked) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
