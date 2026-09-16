package io.github.scottcooper92.binge.seerr.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SurfaceDefaults
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.theme.SeerrBrand
import io.github.scottcooper92.binge.seerr.theme.SeerrTvColorScheme
import androidx.tv.material3.Surface as TvSurface

/**
 * This app's counterparts of the design system's `ScreenshotTheme` / `TvScreenshotTheme` harness,
 * wearing [SeerrBrand] instead of Binge's. The submodule's own wrappers have no scheme parameter to
 * thread one through — [PreviewWrapperProvider] takes no constructor arguments — so every frame this
 * app records needs its own wrapper rather than the shared one (#281).
 */
@Composable
fun SeerrScreenshotTheme(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BingeExpressiveTheme(dynamicColor = false, reduceMotion = true, brand = SeerrBrand) {
        Surface(modifier = modifier) {
            content()
        }
    }
}

/** Applies [SeerrScreenshotTheme] to every cell of [SeerrScreenPreviews] and its siblings. */
class SeerrScreenshotThemeWrapper : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) {
        SeerrScreenshotTheme {
            content()
        }
    }
}

/** [SeerrTvScreenshotThemeOnBlack]'s pair — the theme's `background`, for a component frame. */
@Composable
fun SeerrTvScreenshotTheme(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SeerrTvScreenshotCanvas(canvas = null, modifier = modifier, content = content)
}

/** The window's black canvas, for a screen-root frame — this app's [SeerrTvColorScheme] version. */
@Composable
fun SeerrTvScreenshotThemeOnBlack(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SeerrTvScreenshotCanvas(canvas = Color.Black, modifier = modifier, content = content)
}

@Composable
private fun SeerrTvScreenshotCanvas(
    canvas: Color?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BingeTvTheme(colorScheme = SeerrTvColorScheme, reduceMotion = true) {
        TvSurface(
            modifier = modifier,
            colors =
                SurfaceDefaults.colors(
                    containerColor = canvas ?: MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
        ) {
            content()
        }
    }
}

/** Applies [SeerrTvScreenshotTheme] to every cell of [SeerrTvPreviews]. */
class SeerrTvScreenshotThemeWrapper : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) {
        SeerrTvScreenshotTheme(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

/** Applies [SeerrTvScreenshotThemeOnBlack] to every cell of [SeerrTvScreenPreviews]. */
class SeerrTvScreenshotThemeOnBlackWrapper : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) {
        SeerrTvScreenshotThemeOnBlack(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
