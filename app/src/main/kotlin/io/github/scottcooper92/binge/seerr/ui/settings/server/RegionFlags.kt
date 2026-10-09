package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.core.os.LocaleListCompat
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.R as DesR

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

/** [code]'s flag in a settings row's icon box, for the region setting's own row. */
@Composable
internal fun RegionFlag(code: String) =
    Box(
        modifier =
            Modifier
                .size(dimensionResource(DesR.dimen.item_group_icon_size))
                .clip(BingeShapes.MoreCard)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(flagOf(code), style = MaterialTheme.typography.titleMedium)
    }
