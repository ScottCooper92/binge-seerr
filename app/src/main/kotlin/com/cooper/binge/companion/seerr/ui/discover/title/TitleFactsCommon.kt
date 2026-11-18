package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import com.binge.designsystem.component.InfoRowEntry
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import java.util.Locale

/** A language code written in the reader's own language, capitalised: "es" reads "Spanish" or "Español". */
internal fun languageName(
    code: String,
    locale: Locale,
): String = Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }

/** The server's other ratings as facts: Binge's page has one row of stats, so these follow the facts instead. */
internal fun ratingEntries(
    resources: Resources,
    locale: Locale,
    ratings: TitleRatings?,
): List<InfoRowEntry> =
    listOf(
        InfoRowEntry(resources.getString(R.string.title_page_stat_critics), ratings?.criticsPercent?.let { "$it%" }),
        InfoRowEntry(resources.getString(R.string.title_page_stat_audience), ratings?.audiencePercent?.let { "$it%" }),
        InfoRowEntry(resources.getString(R.string.title_page_stat_imdb), ratings?.imdbScore?.let { "%.1f".format(locale, it) }),
    )
