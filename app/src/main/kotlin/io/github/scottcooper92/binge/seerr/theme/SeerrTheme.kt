package io.github.scottcooper92.binge.seerr.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.binge.designsystem.theme.BingeBrand
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.designsystem.theme.DarkColorScheme
import com.binge.designsystem.theme.LightColorScheme
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.binge.designsystem.tv.theme.toTvColorScheme
import androidx.tv.material3.ColorScheme as TvColorScheme

/**
 * This app's own accent: the icon's indigo, not Binge's amber (#281). `ic_launcher_background.xml`
 * already tints the launcher and the TV banner towards it — every screen behind them should agree.
 *
 * Every slot but the brand family — `primary`/`secondary`/`tertiary` and their containers, plus
 * `inversePrimary` — is [DarkColorScheme]/[LightColorScheme] unchanged: the neutral surface ramp and
 * `error` are shared meaning, not brand, and stay Binge's.
 */
private val Indigo100 = Color(0xFFE0E7FF)
private val Indigo200 = Color(0xFFC7D2FE)
private val Indigo300 = Color(0xFFA5B4FC)
private val Indigo400 = Color(0xFF818CF8)
private val IndigoBrand500 = Color(0xFF6366F1)
private val Indigo600 = Color(0xFF4F46E5)
private val Indigo900 = Color(0xFF312E81)
private val IndigoInk = Color(0xFF1E1B4B)

/**
 * Tertiary accent — rose, indigo's complement. Teal was amber's contrast hue (#33); an indigo primary
 * (~hue 235) sits close enough to `BingeSentiment.Info`'s blue (~hue 206) that a second cool accent
 * would read as a third blue, so this picks a warm hue instead, clear of both `Info` and the amber
 * `BingeSentiment.Caution` frees up by moving off brand.
 */
private val Rose100 = Color(0xFFFFE4E6)
private val Rose200 = Color(0xFFFECDD3)
private val Rose400 = Color(0xFFFB7185)
private val Rose600 = Color(0xFFE11D48)
private val Rose900 = Color(0xFF881337)
private val RoseInk = Color(0xFF4C0519)

val SeerrDarkColorScheme: ColorScheme =
    DarkColorScheme.copy(
        primary = Indigo400,
        onPrimary = IndigoInk,
        primaryContainer = Indigo900,
        onPrimaryContainer = Indigo200,
        // Not flipped like primary: inversePrimary pairs with no text (TokenContrastTest doesn't hold it
        // to 4.5:1), so it carries the undiluted brand hue rather than a theme-flipped tone.
        inversePrimary = IndigoBrand500,
        secondary = Indigo300,
        onSecondary = IndigoInk,
        secondaryContainer = Indigo900,
        onSecondaryContainer = Indigo200,
        tertiary = Rose400,
        onTertiary = RoseInk,
        tertiaryContainer = Rose900,
        onTertiaryContainer = Rose200,
    )

val SeerrLightColorScheme: ColorScheme =
    LightColorScheme.copy(
        primary = Indigo600,
        onPrimary = Color.White,
        primaryContainer = Indigo100,
        onPrimaryContainer = IndigoInk,
        inversePrimary = IndigoBrand500,
        secondary = Indigo600,
        onSecondary = Color.White,
        secondaryContainer = Indigo100,
        onSecondaryContainer = IndigoInk,
        tertiary = Rose600,
        onTertiary = Color.White,
        tertiaryContainer = Rose100,
        onTertiaryContainer = RoseInk,
    )

/** [BingeBrand.Binge], with this app's indigo in place of Binge's amber. */
val SeerrBrand = BingeBrand(light = SeerrLightColorScheme, dark = SeerrDarkColorScheme)

/** [SeerrDarkColorScheme] projected for the TV surface — dark-only, like [BingeTvTheme] itself. */
val SeerrTvColorScheme: TvColorScheme = SeerrDarkColorScheme.toTvColorScheme()

/** [BingeExpressiveTheme] wearing [SeerrBrand], so every call site reads this app's accent for free. */
@Composable
fun SeerrTheme(content: @Composable () -> Unit) {
    BingeExpressiveTheme(brand = SeerrBrand, content = content)
}

/** [BingeTvTheme] wearing [SeerrTvColorScheme], the TV counterpart of [SeerrTheme]. */
@Composable
fun SeerrTvTheme(content: @Composable () -> Unit) {
    BingeTvTheme(colorScheme = SeerrTvColorScheme, content = content)
}
