package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleStatuses
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.data.toTitleStatuses
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.homelab.core.presentation.paging.PagedListState
import com.cooper.homelab.core.presentation.search.QuerySearchState

/** What [TitleStatusStore] knows of every title, as a list of cards reads it to show the latest status. */
typealias TitleStatusMap = Map<TitleKey, TitleStatuses>

fun TitleKind.titleType(): TitleType? =
    when (this) {
        TitleKind.Movie -> TitleType.Movie
        TitleKind.Series -> TitleType.Tv
        TitleKind.Person, TitleKind.Collection -> null
    }

/** The status the store holds for a title, as a card shows it; [fallback] is what the card says for a title the store has not seen. */
internal fun TitleStatusMap.availabilityOf(
    kind: TitleKind,
    tmdbId: Int,
    fallback: TitleAvailability,
): TitleAvailability {
    val type = kind.titleType() ?: return fallback
    val held = this[TitleKey(type, tmdbId)] ?: return fallback
    return cardAvailability(held.standard, held.fourK)
}

fun TitleResult.overlaid(statuses: TitleStatusMap): TitleResult =
    statuses.availabilityOf(kind, tmdbId, availability).let { if (it == availability) this else copy(availability = it) }

fun HubCard.overlaid(statuses: TitleStatusMap): HubCard =
    statuses.availabilityOf(kind, tmdbId, availability).let { if (it == availability) this else copy(availability = it) }

fun HubHero.overlaid(statuses: TitleStatusMap): HubHero =
    statuses.availabilityOf(kind, tmdbId, availability).let { if (it == availability) this else copy(availability = it) }

fun PersonCredit.overlaid(statuses: TitleStatusMap): PersonCredit =
    statuses.availabilityOf(kind, tmdbId, availability).let { if (it == availability) this else copy(availability = it) }

fun PersonCredits.overlaid(statuses: TitleStatusMap): PersonCredits =
    PersonCredits(cast.overlaidBy(statuses) { it.overlaid(statuses) }, crew.overlaidBy(statuses) { it.overlaid(statuses) })

fun CollectionDetails.overlaid(statuses: TitleStatusMap): CollectionDetails =
    parts.overlaidBy(statuses) { it.overlaid(statuses) }.let { if (it === parts) this else copy(parts = it) }

/** The list itself when no item changed, so a status that moved elsewhere does not rebuild a list it is not in. */
internal fun <T> List<T>.overlaidBy(
    statuses: TitleStatusMap,
    overlay: (T) -> T,
): List<T> {
    if (statuses.isEmpty()) return this
    val mapped = map(overlay)
    return if (mapped.indices.all { mapped[it] === this[it] }) this else mapped
}

fun PagedListState<HubCard>.overlaid(statuses: TitleStatusMap): PagedListState<HubCard> =
    if (this is PagedListState.Ready) {
        items.overlaidBy(statuses) { it.overlaid(statuses) }.let { if (it === items) this else copy(items = it) }
    } else {
        this
    }

fun QuerySearchState<TitleResult>.overlaid(statuses: TitleStatusMap): QuerySearchState<TitleResult> =
    if (this is QuerySearchState.Results) {
        items.overlaidBy(statuses) { it.overlaid(statuses) }.let { if (it === items) this else copy(items = it) }
    } else {
        this
    }

/**
 * Records the status a list's rows carry, for [read] to order against actions made while the list was loading. A row with no
 * `mediaInfo` says the server holds nothing for the title, which is not news about a title this process already knows, so it
 * leaves what is held alone (a watchlist row still records that it is on the list).
 */
internal fun TitleStatusStore.seedRows(
    read: Long,
    rows: List<Triple<TitleKind?, Int?, SeerrMediaInfoDto?>>,
    onWatchlist: Boolean? = null,
) {
    rows.forEach { (kind, tmdbId, info) ->
        val type = kind?.titleType() ?: return@forEach
        if (tmdbId == null) return@forEach
        val key = TitleKey(type, tmdbId)
        if (info != null) seed(key, info.toTitleStatuses().copy(onWatchlist = onWatchlist), read) else markWatchlist(key, onWatchlist, read)
    }
}

/** The title was taken off the watchlist since the list showing it was read. Nothing known of it is not a reason to hide it. */
internal fun TitleStatusMap.offWatchlist(
    kind: TitleKind,
    tmdbId: Int,
): Boolean = kind.titleType()?.let { this[TitleKey(it, tmdbId)]?.onWatchlist } == false

/** A watchlist's cards without the titles that came off it since it was read. Adding to it shows on the next read, as #2341 says. */
fun List<HubCard>.withoutOffWatchlist(statuses: TitleStatusMap): List<HubCard> =
    if (statuses.isEmpty()) this else filterNot { statuses.offWatchlist(it.kind, it.tmdbId) }.let { if (it.size == size) this else it }

fun PagedListState<HubCard>.withoutOffWatchlist(statuses: TitleStatusMap): PagedListState<HubCard> =
    if (this is PagedListState.Ready) {
        items.withoutOffWatchlist(statuses).let { if (it === items) this else copy(items = it) }
    } else {
        this
    }
