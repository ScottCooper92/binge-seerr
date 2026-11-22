package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.TitleProvider
import com.cooper.binge.companion.seerr.discover.title.TitleProviders
import com.binge.designsystem.R as DesR

/** The ways the server says a title can be watched: through a subscription, or bought. It has no rent or free. */
internal enum class WatchKind { Stream, Buy }

/** The kinds that have at least one provider, in the order the page offers them. A kind with none is not offered. */
internal fun TitleProviders.availableKinds(): List<WatchKind> =
    buildList {
        if (streaming.isNotEmpty()) add(WatchKind.Stream)
        if (buy.isNotEmpty()) add(WatchKind.Buy)
    }

internal fun TitleProviders.forKind(kind: WatchKind): List<TitleProvider> =
    when (kind) {
        WatchKind.Stream -> streaming
        WatchKind.Buy -> buy
    }.distinctBy { it.id }

/**
 * Where the title can be watched in the server's region: a Stream and a Buy chip for the kinds that have providers, then
 * the providers' logos and names in the server's order, and the JustWatch credit the data needs. Nothing is drawn when
 * there are no providers at all. A tap on a logo opens TMDB's watch page for the region, where it gave one.
 */
@Composable
internal fun WhereToWatchSection(
    providers: TitleProviders?,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = providers?.availableKinds().orEmpty()
    if (providers == null || kinds.isEmpty()) return
    var chosen by rememberSaveable { mutableStateOf(WatchKind.Stream) }
    val selected = chosen.takeIf { it in kinds } ?: kinds.first()
    Column(modifier) {
        SectionHeader(stringResource(R.string.title_page_where_to_watch))
        BingeFilterChipRow(
            items = kinds.map { FilterChipItem(label = stringResource(it.labelRes()), icon = it.icon()) },
            selectedIndex = kinds.indexOf(selected),
            onSelect = { chosen = kinds[it] },
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        LazyRow(
            contentPadding = PaddingValues(horizontal = dimensionResource(DesR.dimen.padding_m)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_sm)),
        ) {
            items(providers.forKind(selected), key = { it.id }) { provider ->
                ProviderLogo(provider, onClick = providers.link?.let { link -> { onOpenLink(link) } })
            }
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_s)))
        Text(
            text = stringResource(R.string.title_page_justwatch),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
    }
}

private fun WatchKind.labelRes() = if (this == WatchKind.Stream) R.string.title_page_watch_stream else R.string.title_page_watch_buy

private fun WatchKind.icon() = if (this == WatchKind.Stream) Icons.Filled.LiveTv else Icons.Filled.ShoppingCart

/**
 * A provider's logo with its name under it. The name is the visible label, so the logo itself says nothing to a screen
 * reader. The column widens with the font scale, so a name wraps at a word and not in the middle of one.
 */
@Composable
private fun ProviderLogo(
    provider: TitleProvider,
    onClick: (() -> Unit)?,
) {
    val opensOutside = stringResource(DesR.string.cd_link_chip_opens_outside, provider.name)
    Column(
        modifier =
            Modifier
                .width(dimensionResource(R.dimen.provider_column_width) * LocalDensity.current.fontScale.coerceAtLeast(1f))
                .let {
                    if (onClick == null) {
                        it
                    } else {
                        it.clickable(role = Role.Button, onClick = onClick).clearAndSetSemantics {
                            contentDescription = opensOutside
                            role = Role.Button
                        }
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            SubcomposeAsyncImage(
                model = provider.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(dimensionResource(R.dimen.provider_logo_size))
                        .clip(BingeShapes.Medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_s)))
        Text(
            text = provider.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
