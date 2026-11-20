package com.cooper.binge.companion.seerr.discover.grid

/** The filters a user sets together on one page of the sheet, so each can be shown, and cleared, as one chip. */
enum class FilterGroup {
    Genres,
    Release,
    Rating,
    Runtime,
    Votes,
    Language,
    Where,
    Certification,
    Keywords,
    ExcludedKeywords,
    Status,
}

fun DiscoverFilters.isSet(group: FilterGroup): Boolean =
    when (group) {
        FilterGroup.Genres -> genres.isNotEmpty()
        FilterGroup.Release -> releaseFrom != null || releaseTo != null
        FilterGroup.Rating -> ratingMin != null || ratingMax != null
        FilterGroup.Runtime -> runtimeMin != null || runtimeMax != null
        FilterGroup.Votes -> voteCountMin != null || voteCountMax != null
        FilterGroup.Language -> language != null
        FilterGroup.Where -> providers.isNotEmpty()
        FilterGroup.Certification -> certification != null
        FilterGroup.Keywords -> keywords.isNotEmpty()
        FilterGroup.ExcludedKeywords -> excludeKeywords.isNotEmpty()
        FilterGroup.Status -> status.isNotEmpty()
    }

/** The groups with something set, in the order the sheet lists them. */
fun DiscoverFilters.setGroups(): List<FilterGroup> = FilterGroup.entries.filter(::isSet)

/** These filters with [group] cleared, and the setting that means nothing without it. */
fun DiscoverFilters.cleared(group: FilterGroup): DiscoverFilters =
    when (group) {
        FilterGroup.Genres -> copy(genres = emptySet(), genresAny = false)
        FilterGroup.Release -> copy(releaseFrom = null, releaseTo = null)
        FilterGroup.Rating -> copy(ratingMin = null, ratingMax = null)
        FilterGroup.Runtime -> copy(runtimeMin = null, runtimeMax = null)
        FilterGroup.Votes -> copy(voteCountMin = null, voteCountMax = null)
        FilterGroup.Language -> copy(language = null)
        FilterGroup.Where -> copy(providers = emptySet(), region = null)
        FilterGroup.Certification -> copy(certification = null, certificationCountry = null)
        FilterGroup.Keywords -> copy(keywords = emptySet())
        FilterGroup.ExcludedKeywords -> copy(excludeKeywords = emptySet())
        FilterGroup.Status -> copy(status = emptySet())
    }

/**
 * These filters with [group] cleared except what [base], the filters the list opened with, set in it: a chip for what the
 * user added drops that, and the row's own filter stays.
 */
fun DiscoverFilters.clearedKeeping(
    group: FilterGroup,
    base: DiscoverFilters,
): DiscoverFilters =
    cleared(group).let { cleared ->
        when (group) {
            FilterGroup.Genres -> cleared.copy(genres = base.genres)
            FilterGroup.Release -> cleared.copy(releaseFrom = base.releaseFrom, releaseTo = base.releaseTo)
            FilterGroup.Language -> cleared.copy(language = base.language)
            FilterGroup.Where -> cleared.copy(providers = base.providers, region = base.region)
            FilterGroup.Keywords -> cleared.copy(keywords = base.keywords)
            FilterGroup.Rating,
            FilterGroup.Runtime,
            FilterGroup.Votes,
            FilterGroup.Certification,
            FilterGroup.ExcludedKeywords,
            FilterGroup.Status,
            -> cleared
        }
    }
