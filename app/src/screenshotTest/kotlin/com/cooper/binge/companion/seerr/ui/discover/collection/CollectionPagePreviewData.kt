package com.cooper.binge.companion.seerr.ui.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails

private const val FIRST_YEAR = 1999
private const val RATING = 8.2
private const val REPEATS = 6

private fun film(
    index: Int,
    name: String,
    availability: TitleAvailability,
    overview: String? = "A hacker learns what the world is, and what it costs to leave it. ".repeat(2),
) = TitleResult(TitleKind.Movie, index, name, "${FIRST_YEAR + index}", null, null, overview, RATING, availability, null)

/** One film in each state the server can hold it in, then one it does not know. */
internal fun mixedCollection() =
    CollectionDetails(
        id = 1,
        name = "The Matrix Collection",
        overview = "A hacker discovers the world is a simulation. ".repeat(REPEATS),
        backdropUrl = null,
        posterUrl = null,
        parts =
            listOf(
                film(0, "The Matrix", TitleAvailability.Available),
                film(1, "The Matrix Reloaded", TitleAvailability.PartiallyAvailable),
                film(2, "The Matrix Revolutions", TitleAvailability.Processing),
                film(3, "The Matrix Resurrections", TitleAvailability.Pending),
                film(4, "The Matrix Origins", TitleAvailability.None),
            ),
    )

internal fun allAvailableCollection() =
    mixedCollection().let { it.copy(parts = it.parts.map { part -> part.copy(availability = TitleAvailability.Available) }) }

internal fun sparseCollection() =
    CollectionDetails(
        id = 2,
        name = "A Pair",
        overview = null,
        backdropUrl = null,
        posterUrl = null,
        parts =
            listOf(
                film(0, "First", TitleAvailability.None, overview = null),
                film(1, "Second", TitleAvailability.None, overview = null),
            ),
    )

internal fun longCollection() =
    mixedCollection().let {
        it.copy(
            name = "The Extended Special Collector's Edition Trilogy Of Considerable Length",
            parts =
                it.parts.mapIndexed { index, part ->
                    part.copy(name = "The Very Long Title Of Film Number ${index + 1} And Its Subtitle")
                },
        )
    }
