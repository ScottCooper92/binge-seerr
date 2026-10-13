package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.TextUnit
import androidx.core.os.LocaleListCompat
import com.binge.designsystem.component.BingeIconPlate
import com.cooper.binge.companion.seerr.R

/** Regions people most often pick, after the device's own, for a region picker's suggestions. */
private val POPULAR_REGIONS = listOf("US", "GB", "CA", "AU", "DE", "FR", "ES", "IN", "BR", "JP")

/** A two-letter region's flag, from its regional indicator symbols; a globe for "all" or the server's default. */
internal fun flagOf(code: String): String {
    val upper = code.uppercase()
    if (upper.length != 2 || !upper.all { it in 'A'..'Z' }) return GLOBE
    return upper.map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }.joinToString("")
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val GLOBE = "🌐"

/** How many regions a picker suggests: enough to cover the likely ones without pushing the list down. */
private const val SUGGESTED_REGIONS = 6

/** The regions a picker suggests: the device's own, in its order of preference, then popular ones, up to a handful. */
internal fun suggestedRegions(): List<String> {
    val locales = LocaleListCompat.getAdjustedDefault()
    val device = (0 until locales.size()).mapNotNull { locales[it]?.country?.takeIf { c -> c.length == 2 } }
    return (device + POPULAR_REGIONS).distinct().take(SUGGESTED_REGIONS)
}

/** [code]'s flag on the design system's icon plate, for the region setting's own row. */
@Composable
internal fun RegionFlag(code: String) =
    BingeIconPlate {
        // The flag is a glyph rather than copy: a larger font scale must not grow it past the plate it sits on.
        val size = with(LocalDensity.current) { dimensionResource(R.dimen.region_flag_size).toSp() }
        Text(flagOf(code), style = MaterialTheme.typography.titleMedium.copy(fontSize = size, lineHeight = TextUnit.Unspecified))
    }
