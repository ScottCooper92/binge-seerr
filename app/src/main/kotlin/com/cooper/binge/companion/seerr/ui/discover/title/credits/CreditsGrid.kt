package com.cooper.binge.companion.seerr.ui.discover.title.credits

import com.binge.designsystem.template.PeopleGridPerson
import com.binge.designsystem.template.PeopleGridSection
import com.cooper.binge.companion.seerr.discover.title.TitleCredits
import com.cooper.binge.companion.seerr.discover.title.TitlePerson

/**
 * The cast and crew as the design system's people grid takes them: the cast as one section, the crew as a section per department.
 * A card's key is text that is unique across both tabs, because the same person can be in two departments and may be listed twice in
 * the cast, so [personId] says whose page a key opens.
 */
internal class CreditsGrid(
    val cast: List<PeopleGridSection>,
    val crew: List<PeopleGridSection>,
    private val personIds: Map<String, Int>,
) {
    fun personId(key: String): Int? = personIds[key]
}

/** [departmentTitles] are the crew departments' names in the user's language, in the same order as [TitleCredits.departments]. */
internal fun creditsGrid(
    credits: TitleCredits,
    departmentTitles: List<String>,
): CreditsGrid {
    val personIds = mutableMapOf<String, Int>()

    fun card(
        key: String,
        person: TitlePerson,
    ): PeopleGridPerson {
        personIds[key] = person.personId
        return PeopleGridPerson(key = key, name = person.name, role = person.role, photoUrl = person.photoUrl)
    }

    val cast =
        credits.cast
            .takeIf { it.isNotEmpty() }
            ?.let { people ->
                listOf(PeopleGridSection(CAST_SECTION, title = null, people = people.mapIndexed { index, it -> card("cast/$index", it) }))
            }.orEmpty()
    val crew =
        credits.departments.mapIndexed { index, department ->
            PeopleGridSection(
                key = "crew/$index",
                title = departmentTitles[index],
                people = department.people.map { card("crew/$index/${it.personId}", it) },
            )
        }
    return CreditsGrid(cast, crew, personIds)
}

private const val CAST_SECTION = "cast"
