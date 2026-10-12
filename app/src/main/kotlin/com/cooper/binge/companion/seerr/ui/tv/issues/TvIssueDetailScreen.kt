package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.TvStableFocusScroll
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import com.binge.designsystem.tv.focus.tvFocusTarget
import com.binge.designsystem.tv.template.TvPageHosting
import com.binge.designsystem.uppercaseLocalised
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.issues.IssueComment
import com.cooper.binge.companion.seerr.ui.issues.IssueDetail
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.issueAffectedLabel
import com.cooper.binge.companion.seerr.ui.issues.labelRes
import com.cooper.binge.companion.seerr.ui.issues.tone
import com.cooper.binge.companion.seerr.ui.tv.TvLoadingPlate
import com.cooper.binge.companion.seerr.ui.tv.TvPoster
import com.cooper.binge.companion.seerr.ui.tv.TvSeerrErrorPage
import com.cooper.binge.companion.seerr.ui.tv.tvColor
import com.cooper.binge.companion.seerr.ui.users.settings.UserRole
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR
import com.cooper.binge.companion.seerr.ui.requests.labelRes as mediaTypeLabelRes
import com.cooper.binge.companion.seerr.ui.state.labelRes as roleLabelRes

/** Everything the TV issue detail page can ask of its host, in one place so the overlay stays a wiring. */
internal class TvIssueDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * One issue as a read-only television page: the title it is about, what it affects, its type and
 * state, and its comment thread — so a reporter can see what the admin replied without reaching for
 * the phone. The same [IssueDetailUiState] the phone's `IssueDetailScreen` renders, over a TV overlay
 * that owns Back exactly as the request page does.
 *
 * Posting, editing and deleting comments stay phone-only — free text on a remote, and long-press
 * actions TV has no equivalent for — and so do resolving, reopening and deleting the issue: those
 * actions stay where they already are, on the board's row. This page only reads.
 */
@Composable
internal fun TvIssueDetailScreen(
    state: IssueDetailUiState,
    actions: TvIssueDetailActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    initiallyFocused: Boolean = false,
) {
    BackHandler(onBack = actions.onBack)
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            // The TV page does not seed from a cached row: it loads, or fails, as it always has.
            IssueDetailUiState.Loading, is IssueDetailUiState.Seeded -> {
                val failure = (state as? IssueDetailUiState.Seeded)?.error
                if (failure == null) {
                    TvLoadingPlate(modifier = Modifier.fillMaxSize(), hosting = TvPageHosting.Overlay)
                } else {
                    TvSeerrErrorPage(failure, actions.onRetry)
                }
            }
            is IssueDetailUiState.Error -> TvSeerrErrorPage(state.error, actions.onRetry)
            is IssueDetailUiState.Ready -> TvIssueDetailContent(detail = state.detail, now = now, initiallyFocused = initiallyFocused)
        }
    }
}

/**
 * The loaded page's content: a fixed-width reading column so a title's meta line does not stretch
 * across a ten-foot screen. Arrival focus lands on the first comment — the report itself is the
 * thread's first entry — so the D-pad can walk straight into reading; an issue with no comments at
 * all lands on the "no comments" line instead, drawn as a read-out row, so the page is never left
 * with nowhere for the D-pad to land. [initiallyFocused] seeds that arrival row as focused, for a frame.
 */
@Composable
private fun TvIssueDetailContent(
    detail: IssueDetail,
    now: Long,
    initiallyFocused: Boolean,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    val comments = detail.comments
    TvStableFocusScroll {
        Column(
            modifier =
                Modifier
                    .width(dimensionResource(R.dimen.tv_detail_content_width))
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                        vertical = dimensionResource(TvR.dimen.tv_overscan_vertical),
                    ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_detail_section_gap)),
        ) {
            TvIssueDetailHeader(detail.item)
            detail.report?.let { report ->
                TvDetailSectionHeader(stringResource(R.string.issue_problem))
                Text(text = report.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            TvDetailSectionHeader(stringResource(R.string.issue_comments_title))
            if (comments.isEmpty()) {
                TvReadOutRow(
                    modifier = Modifier.tvArrivalTarget(arrival),
                    initiallyFocused = initiallyFocused,
                ) { focused ->
                    Text(
                        text = stringResource(R.string.issue_no_comments),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            } else {
                comments.forEachIndexed { index, comment ->
                    TvCommentRow(
                        comment,
                        now = now,
                        modifier = if (index == 0) Modifier.tvArrivalTarget(arrival) else Modifier,
                        initiallyFocused = initiallyFocused && index == 0,
                    )
                }
            }
        }
    }
}

/** The title it is about: poster, title, what it affects, and the type and state. */
@Composable
private fun TvIssueDetailHeader(item: IssueItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        TvPoster(url = item.posterUrl)
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xxs))) {
            Text(
                text = item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    issueAffectedLabel(item)
                        ?: listOfNotNull(
                            stringResource(item.mediaType.mediaTypeLabelRes()),
                            item.year,
                        ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(item.type.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(item.status.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = item.status.tone().tvColor(),
                )
            }
        }
    }
}

/**
 * A read-out, never a press: focusable purely so the D-pad can walk (and scroll) past it to reach what follows.
 * It is a focus target and not a disabled button, so a screen reader does not announce it as one.
 */
@Composable
private fun TvReadOutRow(
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BingeShapes.TvListItem)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.TvListItem)
                .tvFocusTarget { focused = it }
                // Merged so the node that carries focus is the one that reads.
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
    ) {
        content(focused)
    }
}

/** One comment in the thread, drawn as a [TvReadOutRow]. */
@Composable
private fun TvCommentRow(
    comment: IssueComment,
    now: Long,
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
) {
    TvReadOutRow(modifier = modifier, initiallyFocused = initiallyFocused) { focused ->
        val content = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurface)
        val muted = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
            ) {
                Text(
                    text = comment.author ?: stringResource(R.string.requests_requester_unknown),
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.role != UserRole.User) TvTag(stringResource(comment.role.roleLabelRes()))
                formatRelativeOrAbsolute(comment.createdAtMillis, now)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                }
            }
            Text(
                text = comment.message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (focused) content else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A compact tag mirroring the phone thread's role tag, built tv-native since `BingeTag` is Material 3. */
@Composable
private fun TvTag(label: String) {
    Box(
        modifier =
            Modifier
                .clip(BingeShapes.Tag)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_s), vertical = dimensionResource(DesR.dimen.tag_padding_v)),
    ) {
        Text(
            text = label.uppercaseLocalised(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
