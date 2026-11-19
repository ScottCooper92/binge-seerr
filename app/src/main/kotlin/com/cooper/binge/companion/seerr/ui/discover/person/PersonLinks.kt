package com.cooper.binge.companion.seerr.ui.discover.person

import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.seerr.isOpenableSite

internal enum class PersonLinkKind { Imdb, Website }

internal data class PersonLink(
    val kind: PersonLinkKind,
    val url: String,
)

private val IMDB_PERSON_ID = Regex("nm\\d+")

/** The pages the server named for this person. An id that is not an IMDb name id, or an address that is not a site, is dropped. */
internal fun PersonDetails.links(): List<PersonLink> =
    listOfNotNull(
        imdbId?.takeIf { IMDB_PERSON_ID.matches(it) }?.let { PersonLink(PersonLinkKind.Imdb, "https://www.imdb.com/name/$it") },
        homepage?.takeIf { isOpenableSite(it) }?.let { PersonLink(PersonLinkKind.Website, it) },
    )
