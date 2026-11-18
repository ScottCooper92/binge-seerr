package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrCreditsDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The departments the list leads with, in this order; any other follows alphabetically. */
private val DEPARTMENT_ORDER = listOf("Directing", "Writing", "Production", "Camera", "Sound", "Art", "Editing")

/** [TitleCreditsRepository] over the Seerr API: a film's credits or a series'. */
class SeerrTitleCreditsRepository(
    private val api: suspend () -> SeerrApi,
    private val io: CoroutineDispatcher,
) : TitleCreditsRepository {
    override suspend fun credits(
        kind: TitleKind,
        tmdbId: Int,
    ): TitleCredits =
        withContext(io) {
            val api = api()
            when (kind) {
                TitleKind.Movie -> api.movieFullDetails(tmdbId).credits
                TitleKind.Series -> api.tvFullDetails(tmdbId).credits
                else -> throw IllegalArgumentException("Only a film or a series has a credits list")
            }.toTitleCredits()
        }
}

internal fun SeerrCreditsDto?.toTitleCredits(): TitleCredits {
    val cast = this?.cast.orEmpty().toTitleCast()
    val departments =
        this
            ?.crew
            .orEmpty()
            .groupBy { it.department.orEmpty() }
            .map { (department, crew) ->
                // One row per person in a department, under all the jobs they held there.
                CreditsDepartment(department, crew.toTitleCrew())
            }.sortedWith(compareBy({ departmentRank(it.name) }, { it.name.isEmpty() }, { it.name }))
    return TitleCredits(cast, departments)
}

private fun departmentRank(name: String): Int = DEPARTMENT_ORDER.indexOf(name).let { if (it < 0) DEPARTMENT_ORDER.size else it }
