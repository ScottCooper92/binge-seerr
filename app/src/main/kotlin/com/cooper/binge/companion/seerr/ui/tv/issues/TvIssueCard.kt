package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.component.BingeTvInitialsAvatar
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.issues.IssueComment
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** How much of a report or comment a card shows before it asks to be opened. */
internal const val ISSUE_CARD_PREVIEW_LINES = 4

/** What a card shows: who wrote it and when, the kind of problem for the report alone, and what they wrote. */
internal data class TvIssueCardContent(
    val author: String,
    val avatarUrl: String?,
    val meta: String,
    val message: String,
    val isMine: Boolean,
    val kind: String? = null,
)

@Composable
internal fun commentCard(
    comment: IssueComment,
    now: Long,
): TvIssueCardContent =
    TvIssueCardContent(
        author = comment.author.orEmpty(),
        avatarUrl = null,
        meta =
            listOfNotNull(comment.author, formatRelativeOrAbsolute(comment.createdAtMillis, now))
                .joinToString(stringResource(R.string.hub_meta_separator)),
        message = comment.message,
        isMine = comment.isMine,
    )

/**
 * A report or a comment as a card: the author's avatar and "name · age", the report's kind in the accent, and the text,
 * held to [maxLines]. The user's own are tinted and squared at the top end, everyone else's squared at the top start, as
 * the phone's bubbles are; the meta line stays at the start either way. Focus fills it, as it fills a row. [onClick] opens
 * the card's own page; a card with none is still focusable, so the D-pad can read down a page.
 */
@Composable
internal fun TvIssueCard(
    card: TvIssueCardContent,
    modifier: Modifier = Modifier,
    maxLines: Int = ISSUE_CARD_PREVIEW_LINES,
    onClick: () -> Unit = {},
    initiallyFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val scheme = MaterialTheme.colorScheme
    val shape = issueCardShape(card.isMine)
    val resting = if (card.isMine) scheme.onPrimaryContainer else scheme.onSurface
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (card.isMine) scheme.primaryContainer else scheme.surfaceVariant)
                .tvFocusFill(isFocused = focused, shape = shape)
                .tvClickable(onFocusChanged = { focused = it }, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_sm)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BingeTvInitialsAvatar(name = card.author, size = dimensionResource(TvR.dimen.tv_button_icon), avatarUrl = card.avatarUrl)
            Text(
                text = card.meta,
                style = MaterialTheme.typography.titleSmall,
                color = tvFocusContentColor(focused, if (card.isMine) scheme.onPrimaryContainer else scheme.onSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        card.kind?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = tvFocusContentColor(focused, scheme.primary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = card.message,
            style = MaterialTheme.typography.bodyLarge,
            color = tvFocusContentColor(focused, resting),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The phone's bubble shape: rounded, with the corner nearest the author squared, top end for the user's own. */
@Composable
private fun issueCardShape(isMine: Boolean): Shape {
    val rounded = dimensionResource(R.dimen.issue_bubble_corner)
    val pointer = dimensionResource(R.dimen.issue_bubble_pointer_corner)
    return RoundedCornerShape(
        topStart = if (isMine) rounded else pointer,
        topEnd = if (isMine) pointer else rounded,
        bottomEnd = rounded,
        bottomStart = rounded,
    )
}
