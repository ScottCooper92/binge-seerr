package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrCreditsDto
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The departments the list leads with, in this order; any other follows alphabetically. */
private val DEPARTMENT_ORDER = listOf("Directing", "Writing", "Production", "Camera", "Sound", "Art", "Editing")

/** [TitleCreditsRepository] over the Seerr API. Only a film has credits here so far; a series arrives with its page. */
class SeerrTitleCreditsRepository(
    private val api: suspend () -> SeerrApi,
    private val io: CoroutineDispatcher,
) : TitleCreditsRepository {
    override suspend fun credits(
        kind: TitleKind,
        tmdbId: Int,
    ): TitleCredits {
        require(kind == TitleKind.Movie) { "Only a film has a credits list so far" }
        return withContext(io) { api().movieFullDetails(tmdbId).credits.toTitleCredits() }
    }
}

internal fun SeerrCreditsDto?.toTitleCredits(): TitleCredits {
    val cast =
        this?.cast.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE }.map {
            TitlePerson(it.id, it.name, it.character?.takeIf(String::isNotBlank), it.profilePath?.toTmdbPosterUrl())
        }
    val departments =
        this
            ?.crew
            .orEmpty()
            .groupBy { it.department.orEmpty() }
            .map { (department, crew) ->
                // One row per person in a department, under all the jobs they held there.
                val people =
                    crew.groupBy { it.id }.values.map { jobs ->
                        val first = jobs.first()
                        val role = jobs.mapNotNull { it.job?.takeIf(String::isNotBlank) }.distinct().joinToString(", ")
                        TitlePerson(first.id, first.name, role.ifEmpty { null }, first.profilePath?.toTmdbPosterUrl())
                    }
                CreditsDepartment(department, people)
            }.sortedWith(compareBy({ departmentRank(it.name) }, { it.name }))
    return TitleCredits(cast, departments)
}

private fun departmentRank(name: String): Int = DEPARTMENT_ORDER.indexOf(name).let { if (it < 0) DEPARTMENT_ORDER.size else it }
