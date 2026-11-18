package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleKind

/** One department's crew. [name] is the department as the server names it (`Directing`); the screen puts it in the user's language. */
data class CreditsDepartment(
    val name: String,
    val people: List<TitlePerson>,
)

/** The whole cast in billing order, and the crew by department. A person in several departments is in each. */
data class TitleCredits(
    val cast: List<TitlePerson>,
    val departments: List<CreditsDepartment>,
) {
    val isEmpty: Boolean get() = cast.isEmpty() && departments.isEmpty()
}

/** What the cast and crew list reads. It knows nothing of Retrofit, so a view model holds this and a test holds a fake. */
interface TitleCreditsRepository {
    suspend fun credits(
        kind: TitleKind,
        tmdbId: Int,
    ): TitleCredits
}
