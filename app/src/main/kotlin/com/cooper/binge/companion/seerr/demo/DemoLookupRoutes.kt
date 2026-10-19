package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrCompanyDto
import com.cooper.binge.companion.seerr.seerr.SeerrCompanyPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrGenreDto
import com.cooper.binge.companion.seerr.seerr.SeerrKeywordDto
import com.cooper.binge.companion.seerr.seerr.SeerrKeywordPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrLanguageDto
import com.cooper.binge.companion.seerr.seerr.SeerrRegionDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchProviderDto

private val REGIONS = listOf(SeerrRegionDto("US", "United States"), SeerrRegionDto("GB", "United Kingdom"), SeerrRegionDto("ES", "Spain"))
private val LANGUAGES = listOf(SeerrLanguageDto("en", "English"), SeerrLanguageDto("es", "Spanish"), SeerrLanguageDto("fr", "French"))
private val GENRES =
    listOf(SeerrGenreDto(28, "Action"), SeerrGenreDto(16, "Animation"), SeerrGenreDto(35, "Comedy"), SeerrGenreDto(18, "Drama"))
private val KEYWORDS = listOf(SeerrKeywordDto(1, "silent film"), SeerrKeywordDto(2, "open source"), SeerrKeywordDto(3, "space"))
private val COMPANIES = listOf(SeerrCompanyDto(1, "Blender Foundation"), SeerrCompanyDto(2, "Demo Studios"))
private val PROVIDERS = listOf(SeerrWatchProviderDto(1, "Demo Stream"), SeerrWatchProviderDto(2, "Sample Plus"))

/** The pickers' lookups: regions, languages, genres, keywords, companies and watch providers, a short fixed list of each. */
internal fun lookupRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "regions") { _, _ -> ok(REGIONS) },
        route("GET", "watchproviders", "regions") { _, _ -> ok(REGIONS) },
        route("GET", "languages") { _, _ -> ok(LANGUAGES) },
        route("GET", "genres", "{}") { _, _ -> ok(GENRES) },
        route("GET", "watchproviders", "{}") { _, _ -> ok(PROVIDERS) },
        route("GET", "search", "keyword") { call, _ -> ok(SeerrKeywordPageDto(KEYWORDS.filter { matches(it.name, call) })) },
        route("GET", "search", "company") { call, _ -> ok(SeerrCompanyPageDto(COMPANIES.filter { matches(it.name, call) })) },
        route("GET", "keyword", "{}") { _, (id) -> KEYWORDS.firstOrNull { it.id == id.toInt() }?.let { ok(it) } ?: demoNotFound },
        route("GET", "studio", "{}") { _, (id) -> COMPANIES.firstOrNull { it.id == id.toInt() }?.let { ok(it) } ?: demoNotFound },
        route("GET", "network", "{}") { _, (id) -> COMPANIES.firstOrNull { it.id == id.toInt() }?.let { ok(it) } ?: demoNotFound },
    )

private fun matches(
    name: String?,
    call: DemoCall,
): Boolean = name.orEmpty().contains(call.query("query").orEmpty(), ignoreCase = true)
