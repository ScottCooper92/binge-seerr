package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrCertificationDto
import com.cooper.binge.companion.seerr.seerr.SeerrCertificationsDto
import com.cooper.binge.companion.seerr.seerr.SeerrCompanyDto
import com.cooper.binge.companion.seerr.seerr.SeerrGenreDto
import com.cooper.binge.companion.seerr.seerr.SeerrKeywordDto
import com.cooper.binge.companion.seerr.seerr.SeerrKeywordPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrLanguageDto
import com.cooper.binge.companion.seerr.seerr.SeerrRegionDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchProviderDto
import com.cooper.homelab.core.server.demo.DemoCall
import com.cooper.homelab.core.server.demo.DemoRoute
import com.cooper.homelab.core.server.demo.route
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val REGIONS = listOf(SeerrRegionDto("US", "United States"), SeerrRegionDto("GB", "United Kingdom"), SeerrRegionDto("ES", "Spain"))
private val CERTIFICATIONS =
    SeerrCertificationsDto(
        mapOf(
            "US" to
                listOf(
                    SeerrCertificationDto("G", 1),
                    SeerrCertificationDto("PG", 2),
                    SeerrCertificationDto("PG-13", 3),
                    SeerrCertificationDto("R", 4),
                ),
            "GB" to
                listOf(
                    SeerrCertificationDto("U", 1),
                    SeerrCertificationDto("12A", 2),
                    SeerrCertificationDto("15", 3),
                    SeerrCertificationDto("18", 4),
                ),
        ),
    )
private val LANGUAGES = listOf(SeerrLanguageDto("en", "English"), SeerrLanguageDto("es", "Spanish"), SeerrLanguageDto("fr", "French"))

/** The genres the demo knows, which its titles are tagged with. */
internal val DEMO_GENRES =
    listOf(SeerrGenreDto(28, "Action"), SeerrGenreDto(16, "Animation"), SeerrGenreDto(35, "Comedy"), SeerrGenreDto(18, "Drama"))
private val KEYWORDS = listOf(SeerrKeywordDto(1, "silent film"), SeerrKeywordDto(2, "open source"), SeerrKeywordDto(3, "space"))
private val COMPANIES = listOf(SeerrCompanyDto(1, "Blender Foundation"), SeerrCompanyDto(2, "Demo Studios"))
private val PROVIDERS =
    listOf(
        SeerrWatchProviderDto(id = 2, name = "Sample Plus", displayPriority = 2),
        SeerrWatchProviderDto(id = 1, name = "Demo Stream", displayPriority = 1),
    )

/** The pickers' lookups: regions, languages, genres, keywords, companies and watch providers, a short fixed list of each. */
internal fun lookupRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "regions") { _, _ -> ok(REGIONS) },
        route("GET", "watchproviders", "regions") { _, _ -> ok(REGIONS) },
        route("GET", "certifications", "{}") { _, _ -> ok(CERTIFICATIONS) },
        route("GET", "languages") { _, _ -> ok(LANGUAGES) },
        route("GET", "genres", "{}") { _, _ -> ok(DEMO_GENRES) },
        route("GET", "watchproviders", "{}") { _, _ -> ok(PROVIDERS) },
        route("GET", "search", "keyword") { call, _ -> ok(SeerrKeywordPageDto(KEYWORDS.filter { matches(it.name, call) })) },
        route("GET", "search", "company") { call, _ -> ok(companySearchPage(COMPANIES.filter { matches(it.name, call) })) },
        route("GET", "keyword", "{}") { _, (id) -> KEYWORDS.firstOrNull { it.id == id.toInt() }?.let { ok(it) } ?: demoNotFound },
        route("GET", "studio", "{}") { _, (id) -> COMPANIES.firstOrNull { it.id == id.toInt() }?.let { ok(it) } ?: demoNotFound },
        route("GET", "network", "{}") { _, (id) -> COMPANIES.firstOrNull { it.id == id.toInt() }?.let { ok(it) } ?: demoNotFound },
    )

/** The company search as the server answers it: TMDB's own page, in snake_case, unlike the slider's studio. */
private fun companySearchPage(found: List<SeerrCompanyDto>): JsonObject =
    buildJsonObject {
        put("page", 1)
        put(
            "results",
            buildJsonArray {
                found.forEach { company ->
                    add(
                        buildJsonObject {
                            put("id", company.id)
                            put("name", company.name)
                            put("logo_path", company.logoPath)
                        },
                    )
                }
            },
        )
        put("total_pages", 1)
        put("total_results", found.size)
    }

private fun matches(
    name: String?,
    call: DemoCall,
): Boolean = name.orEmpty().contains(call.query("query").orEmpty(), ignoreCase = true)
