package com.cooper.binge.companion.seerr.ui.discover.request

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices

/** One film of a collection as the sheet lists it. It is open to request in a version only while the server holds nothing of it and has no request for it. */
data class RequestFilm(
    val tmdbId: Int,
    val title: String,
    val year: String?,
    val availability: TitleAvailability,
    val availability4k: TitleAvailability = TitleAvailability.None,
) {
    /** What the server holds of the version being requested, standard or 4K. */
    fun availability(use4k: Boolean): TitleAvailability = if (use4k) availability4k else availability

    fun open(use4k: Boolean): Boolean = availability(use4k) == TitleAvailability.None
}

/**
 * The title a request sheet is for. [seasons] is empty for a movie, and for a series means the whole show. [standardOpen] and
 * [fourKOpen] say which versions the server holds nothing of, so the sheet never offers one that is already requested.
 * [seasonChoices] is a show's seasons per version, and [partialRequests] is the server's setting for sending only some.
 * [posterUrl] and [meta] ("2023 · 2h 1m") head the sheet. [films] is the films of a collection, in release order: a collection is
 * requested one film at a time in the chosen version, so its [tmdbId] is the collection's; [standardOpen] and [fourKOpen] say whether any
 * film is open in each version.
 */
data class RequestSheetTarget(
    val kind: TitleKind,
    val tmdbId: Int,
    val title: String,
    val seasons: List<Int> = emptyList(),
    val standardOpen: Boolean = true,
    val fourKOpen: Boolean = true,
    val seasonChoices: SeasonChoices? = null,
    val partialRequests: Boolean = true,
    val posterUrl: String? = null,
    val meta: String? = null,
    val films: List<RequestFilm> = emptyList(),
) {
    val isCollection: Boolean get() = films.isNotEmpty()
}

/** Why a send failed, in the terms the dialog explains. [detail] is the server's own sentence, where it gave one. */
data class RequestProblem(
    val cause: RequestProblemCause,
    val detail: String?,
)

enum class RequestProblemCause { Quota, NotAllowed, Unreachable, Other }

/** The quota read. [Known] holds null where the server does not meter this kind of title, which is no row at all. */
sealed interface QuotaState {
    data object Loading : QuotaState

    data class Known(
        val quota: RequestQuota?,
    ) : QuotaState

    data object Failed : QuotaState
}

/** The servers the user may choose between, read once and kept. */
sealed interface ServersState {
    data object Loading : ServersState

    data class Ready(
        val servers: List<RequestServer>,
    ) : ServersState

    data object Failed : ServersState
}

/** The chosen server's profiles and folders. While they are not [Ready] the sheet will not send what it cannot show. */
sealed interface ChoicesState {
    data object Loading : ChoicesState

    data class Ready(
        val choices: RequestChoices,
    ) : ChoicesState

    data object Failed : ChoicesState
}

/** Where the request goes when the user may choose a destination. */
data class DestinationPick(
    val serverId: Int,
    val profileId: Int?,
    val rootFolder: String?,
    val choices: ChoicesState,
) {
    /** The lists the chosen server gave, with the current pick kept where it still offers it, else its first. */
    fun withChoices(loaded: RequestChoices): DestinationPick =
        copy(
            choices = ChoicesState.Ready(loaded),
            profileId = loaded.profiles.firstOrNull { it.id == profileId }?.id ?: loaded.profiles.firstOrNull()?.id ?: profileId,
            rootFolder = loaded.rootFolders.firstOrNull { it == rootFolder } ?: loaded.rootFolders.firstOrNull() ?: rootFolder,
        )
}
