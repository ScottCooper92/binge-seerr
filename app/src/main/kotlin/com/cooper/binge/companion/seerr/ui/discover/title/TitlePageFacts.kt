package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Translate
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import java.text.NumberFormat
import java.util.Locale

/** The film's facts as the rows of one group: each has an icon, and none opens anything. A fact the server did not give is left out. */
internal fun MovieDetails.factRows(resources: Resources): List<ListItem> {
    fun row(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: Int,
        value: String?,
    ) = value?.takeIf { it.isNotBlank() }?.let { ListItem(icon = icon, label = resources.getString(label), detail = it, clickable = false) }
    val money = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }
    return listOfNotNull(
        row(Icons.Filled.Info, R.string.title_page_fact_status, status),
        row(Icons.Filled.Language, R.string.title_page_fact_original_language, originalLanguage?.let { Locale(it).getDisplayLanguage() }),
        row(Icons.Filled.Translate, R.string.title_page_fact_spoken_languages, spokenLanguages.joinToString(", ")),
        row(Icons.Filled.Business, R.string.title_page_fact_studios, studios.joinToString(", ")),
        row(Icons.Filled.AttachMoney, R.string.title_page_fact_budget, budget?.let(money::format)),
        row(Icons.Filled.Paid, R.string.title_page_fact_revenue, revenue?.let(money::format)),
    )
}
