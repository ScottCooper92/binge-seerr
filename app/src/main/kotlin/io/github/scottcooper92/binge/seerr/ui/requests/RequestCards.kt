package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.InfoValue
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import com.binge.designsystem.R as DesR

/**
 * This request as one card on the same clipped surface a settings group uses: its title beside its state,
 * who asked and who changed it as settings-style rows, what it asked for and where it was sent, and a
 * centred Review or Manage button last.
 */
@Composable
internal fun RequestCard(
    detail: RequestDetail,
    onClick: (() -> Unit)?,
    onOpenUser: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val people = requestPeopleFacts(detail, onOpenUser).filterNot { it.isBlank() }
    val destination = requestDestinationFacts(detail).filter { it.icon != Icons.Filled.Sell && !it.isBlank() }
    val summary = detail.summaries().first()
    val hasInfo = destination.isNotEmpty() || summary.is4k || summary.seasonNumbers.isNotEmpty()
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
            SummaryStatus(summary)
        }
        people.forEach { CardPersonRow(it) }
        if (people.isNotEmpty() && hasInfo) CardDivider()
        if (hasInfo) CardInfoRows(summary, destination)
        if (onClick != null) {
            BingeTextButton(label = actionLabel, onClick = onClick, modifier = Modifier.align(Alignment.CenterHorizontally))
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
) {
    Column(modifier = modifier.padding(horizontal = resolvedContentInset())) {
        RequestCard(
            detail = detail,
            onClick = { onOpenRequest(detail.item.id) }.takeIf { detail.hasPrimaryAction },
            onOpenUser = onOpenUser,
        )
        if (detail.siblings.isNotEmpty()) {
            RequestSummaryGroup(detail.siblings, onClick = onOpenRequest)
        }
    }
}

/** Every other request against the title as a settings group, each row opening its own actions. */
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
                    start = dimensionResource(DesR.dimen.settings_group_row_padding_h),
                    top = dimensionResource(DesR.dimen.section_header_padding_v),
                    bottom = dimensionResource(DesR.dimen.padding_s),
                ).semantics { heading() },
    )
    SettingsGroup(
        title = null,
        rows =
            summaries.map { summary ->
                SettingsRow(
                    icon = Icons.Filled.Person,
                    label = summary.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                    detail =
                        listOfNotNull(
                            formatRelativeOrAbsolute(summary.requestedAtMillis),
                            stringResource(R.string.settings_service_4k).takeIf { summary.is4k },
                            summary.seasonNumbers
                                .takeIf { it.isNotEmpty() }
                                ?.let { pluralStringResource(R.plurals.requests_seasons, it.size, it.joinToString(", ")) },
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

/** A settings-style row: the icon box, a title, the date under it, and a chevron where the row opens that user. */
@Composable
private fun CardPersonRow(fact: Fact) {
    val link = fact.primary as? InfoValue.Link
    val name = fact.primary.plainText()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (link != null) it.clickable(role = Role.Button, onClick = link.onClick) else it }
                .padding(cardRowPadding()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FactIconBox(fact)
        Spacer(Modifier.width(dimensionResource(DesR.dimen.account_card_spacing)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (fact.secondary != null && name.isNotBlank()) "${fact.label} $name" else fact.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            (fact.secondary ?: name).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (link != null) CardChevron()
    }
}

/** What it asked for as a row of its own, then server, quality and folder side by side. */
@Composable
private fun CardInfoRows(
    summary: RequestSummary,
    destination: List<Fact>,
) {
    val asked =
        listOfNotNull(
            summary.seasonNumbers
                .takeIf { it.isNotEmpty() }
                ?.let { pluralStringResource(R.plurals.requests_seasons, it.size, it.joinToString(", ")) },
            stringResource(R.string.settings_service_4k).takeIf { summary.is4k },
        ).joinToString(stringResource(R.string.hub_meta_separator))
    if (asked.isNotEmpty()) {
        CardPersonRow(Fact(Icons.Filled.Info, stringResource(R.string.request_card_info), primary = "", secondary = asked))
    }
    if (destination.isNotEmpty()) CardDestinationLine(destination)
}

@Composable
private fun FactIconBox(
    fact: Fact,
    contentDescription: String? = null,
) {
    Box(
        modifier =
            Modifier
                .size(dimensionResource(DesR.dimen.settings_group_icon_size))
                .clip(BingeShapes.MoreCard)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fact.icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimensionResource(DesR.dimen.settings_group_icon_glyph)),
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
private fun Modifier.cardSurface(): Modifier = clip(BingeShapes.Large).background(MaterialTheme.colorScheme.surfaceContainer)

@Composable
private fun cardRowPadding() =
    PaddingValues(
        horizontal = dimensionResource(DesR.dimen.settings_group_row_padding_h),
        vertical = dimensionResource(DesR.dimen.settings_group_row_padding_v),
    )

@Composable
private fun CardDivider() {
    HorizontalDivider(
        thickness = dimensionResource(DesR.dimen.hairline_thickness),
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = dimensionResource(DesR.dimen.settings_group_row_padding_h)),
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
