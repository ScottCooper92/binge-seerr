package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.InfoValue
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ItemRows
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRanges
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import com.binge.designsystem.R as DesR

private const val ACTING_ALPHA = 0.5f

/**
 * This request as one card on the same clipped surface an item group uses: its title beside its state,
 * who asked and who changed it as settings-style rows, what it asked for and where it was sent, and a
 * centred Review or Manage button last.
 */
@Composable
internal fun RequestCard(
    detail: RequestDetail,
    onClick: (() -> Unit)?,
    onOpenUser: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isActing: Boolean = false,
) {
    val people = requestPeopleFacts(detail, onOpenUser).filterNot { it.isBlank() }
    val destination = requestDestinationFacts(detail).filterNot { it.isBlank() }
    val tags = detail.destination?.tagsLabel
    val summary = detail.summaries().first()
    val hasInfo = destination.isNotEmpty() || tags != null || summary.is4k || summary.seasonNumbers.isNotEmpty()
    val reviewable = detail.actions.canApprove || detail.actions.canRetry
    val actionLabel = stringResource(if (reviewable) R.string.request_primary_review else R.string.request_primary_manage)
    Column(modifier = modifier.fillMaxWidth().cardSurface()) {
        Row(modifier = Modifier.fillMaxWidth().padding(cardRowPadding()), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.request_this_request_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box(Modifier.alpha(if (isActing) ACTING_ALPHA else 1f)) { SummaryStatus(summary) }
        }
        if (people.isNotEmpty()) ItemRows(people.map { it.toListItem() })
        if (people.isNotEmpty() && hasInfo) CardDivider()
        if (hasInfo) CardInfoRows(summary, destination, tags)
        if (onClick != null) {
            BingeTextButton(
                label = actionLabel,
                onClick = onClick,
                enabled = !isActing,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * "This request" as a card and, where the title has others, "Other requests" as a group under it. The
 * card's button and each other row open that request's actions, so the page needs no footer button.
 */
@Composable
internal fun RequestCardSection(
    detail: RequestDetail,
    onOpenRequest: (Int) -> Unit,
    onOpenUser: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isActing: Boolean = false,
) {
    Column(modifier = modifier.padding(resolvedContentPadding())) {
        RequestCard(
            detail = detail,
            onClick = { onOpenRequest(detail.item.id) }.takeIf { detail.hasPrimaryAction },
            onOpenUser = onOpenUser,
            isActing = isActing,
        )
        if (detail.siblings.isNotEmpty()) {
            RequestSummaryGroup(detail.siblings, onClick = onOpenRequest)
        }
    }
}

/** Every other request against the title as an item group, each row opening its own actions. */
@Composable
internal fun RequestSummaryGroup(
    summaries: List<RequestSummary>,
    onClick: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.request_summaries_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .padding(
                    start = dimensionResource(DesR.dimen.item_group_row_padding_h),
                    top = dimensionResource(DesR.dimen.section_header_padding_v),
                    bottom = dimensionResource(DesR.dimen.padding_s),
                ).semantics { heading() },
    )
    ItemGroup(
        title = null,
        rows =
            summaries.map { summary ->
                val requester = summary.requestedBy ?: stringResource(R.string.requests_requester_unknown)
                ListItem(
                    icon = Icons.Filled.Person,
                    leadingContent = {
                        BingeInitialsAvatar(
                            name = requester,
                            avatarUrl = summary.requestedByAvatarUrl,
                            size = dimensionResource(DesR.dimen.avatar_size_md),
                        )
                    },
                    label = requester,
                    detail =
                        listOfNotNull(
                            formatRelativeOrAbsolute(summary.requestedAtMillis),
                            stringResource(R.string.settings_service_4k).takeIf { summary.is4k },
                            summary.seasonNumbers
                                .takeIf { it.isNotEmpty() }
                                ?.let { pluralStringResource(R.plurals.requests_seasons, it.size, it.formatRanges()) },
                        ).joinToString(stringResource(R.string.hub_meta_separator)),
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SummaryStatus(summary)
                            CardChevron()
                        }
                    },
                    onClick = { onClick(summary.id) },
                )
            },
    )
}

/**
 * A fact as a list row, which is what a person is everywhere else: the requester and whoever changed it lead with their
 * avatar, the rest with their icon, and a fact that links to a user opens them under a chevron.
 */
@Composable
private fun Fact.toListItem(): ListItem {
    val link = primary as? InfoValue.Link
    val name = primary.plainText()
    val title = if (secondary != null && name.isNotBlank()) "$label $name" else label
    return ListItem(
        icon = icon,
        leadingContent =
            if (person) {
                { BingeInitialsAvatar(name = name, avatarUrl = avatarUrl, size = dimensionResource(DesR.dimen.avatar_size_md)) }
            } else {
                null
            },
        label = title,
        detail = (secondary ?: name).takeIf { it.isNotBlank() },
        clickable = link != null,
        onClick = { link?.onClick?.invoke() },
    )
}

/** What it asked for as a row of its own, then server, quality and folder side by side, then its tags. */
@Composable
private fun CardInfoRows(
    summary: RequestSummary,
    destination: List<Fact>,
    tags: String?,
) {
    val asked =
        listOfNotNull(
            summary.seasonNumbers
                .takeIf { it.isNotEmpty() }
                ?.let { pluralStringResource(R.plurals.requests_seasons, it.size, it.formatRanges()) },
            stringResource(R.string.settings_service_4k).takeIf { summary.is4k },
        ).joinToString(stringResource(R.string.hub_meta_separator))
    if (asked.isNotEmpty()) {
        ItemRows(listOf(Fact(Icons.Filled.Info, stringResource(R.string.request_card_info), primary = "", secondary = asked).toListItem()))
    }
    if (destination.isNotEmpty()) CardDestinationLine(destination)
    // Tags are free text of any length, so they get a row of their own rather than a column of the line above.
    tags?.let {
        ItemRows(listOf(Fact(Icons.Filled.Sell, stringResource(R.string.request_tags), primary = "", secondary = it).toListItem()))
    }
}

@Composable
private fun FactIconBox(
    fact: Fact,
    contentDescription: String? = null,
) {
    Box(
        modifier =
            Modifier
                .size(dimensionResource(DesR.dimen.item_group_icon_size))
                .clip(BingeShapes.MoreCard)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fact.icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimensionResource(DesR.dimen.item_group_icon_glyph)),
        )
    }
}

/** Server, quality and folder side by side: each an icon over its value alone, the icon standing in for the label. */
@Composable
private fun CardDestinationLine(facts: List<Fact>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(cardRowPadding()),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        facts.forEach { fact ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
            ) {
                FactIconBox(fact, contentDescription = fact.label)
                Text(
                    text = fact.primary.plainText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun InfoValue.plainText(): String =
    when (this) {
        is InfoValue.Plain -> text
        is InfoValue.Link -> text
        is InfoValue.Links -> links.joinToString(", ") { it.text }
    }

private fun Fact.isBlank(): Boolean = primary.plainText().isBlank()

@Composable
internal fun Modifier.cardSurface(): Modifier = clip(BingeShapes.Large).background(MaterialTheme.colorScheme.surfaceContainer)

@Composable
internal fun cardRowPadding() =
    PaddingValues(
        horizontal = dimensionResource(DesR.dimen.item_group_row_padding_h),
        vertical = dimensionResource(DesR.dimen.item_group_row_padding_v),
    )

@Composable
private fun CardDivider() {
    HorizontalDivider(
        thickness = dimensionResource(DesR.dimen.hairline_thickness),
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = dimensionResource(DesR.dimen.item_group_row_padding_h)),
    )
}

@Composable
private fun SummaryStatus(summary: RequestSummary) {
    RequestStateChip(status = summary.status ?: SeerrRequestStatusCode.Pending)
}

@Composable
private fun CardChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
