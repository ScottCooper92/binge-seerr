package com.cooper.binge.companion.seerr.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.binge.designsystem.preview.FOLDABLE_PORTRAIT_SPEC
import com.binge.designsystem.preview.PHONE_LANDSCAPE_SPEC
import com.binge.designsystem.preview.PHONE_PORTRAIT_SPEC
import com.binge.designsystem.preview.SOURCE_LOCALE
import com.binge.designsystem.preview.ScreenshotThemeWrapper
import com.binge.designsystem.preview.TABLET_LANDSCAPE_SPEC
import com.binge.designsystem.preview.TALL_COMPONENT_HEIGHT_DP
import com.binge.designsystem.theme.BingeBrand
import com.binge.designsystem.tv.preview.TV_PREVIEW_SPEC
import com.binge.designsystem.tv.preview.TvScreenshotThemeOnBlackWrapper
import com.binge.designsystem.tv.preview.TvScreenshotThemeWrapper
import com.cooper.binge.companion.seerr.theme.SeerrBrand

/*
 * This app's multipreviews: the design system's cells, bound to [SeerrBrand] instead of Binge's.
 *
 * A multipreview binds its theme wrapper by class, so an app with its own brand cannot take the shared
 * annotations (#281). It subclasses the wrappers below and declares annotations of its own from the design system's
 * public specs and [SOURCE_LOCALE], so a cell here is the shared cell's window, theme and locale, and its baseline
 * keeps the same file name. The locale pin is the design system's now: without it this single-module app's debug
 * variant, which carries the AAPT2 pseudolocales, would record every string accented and bracketed.
 *
 * Not every shared cell has a counterpart. `tablet` is left out of [SeerrScreenPreviews] on purpose: this app's layouts
 * change only at 600dp and 840dp, so a tablet frame showed the foldable's layout, wider. The design system's
 * `ScreenPreviewsWithoutTablet` is the same four cells. Add the tablet back if a screen gains a breakpoint above 840dp.
 */

/** The phone and TV harness themes, wearing [SeerrBrand]. */
class SeerrScreenshotThemeWrapper : ScreenshotThemeWrapper() {
    override val brand: BingeBrand = SeerrBrand
}

/** The TV harness painting the theme's `background`, for a component. */
class SeerrTvScreenshotThemeWrapper : TvScreenshotThemeWrapper() {
    override val brand: BingeBrand = SeerrBrand
}

/** The TV harness painting the window's black, for a screen root. */
class SeerrTvScreenshotThemeOnBlackWrapper : TvScreenshotThemeOnBlackWrapper() {
    override val brand: BingeBrand = SeerrBrand
}

/** The design system's `ScreenPreviewsWithoutTablet`: phone, phone-light, phone-land and foldable. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "phone-light", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_NO, locale = SOURCE_LOCALE)
@Preview(name = "phone-land", device = PHONE_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "foldable", device = FOLDABLE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrScreenPreviews

/** The design system's `ScreenStatePreview`: the `phone` cell, for a state of a screen whose layout frame has the matrix. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrScreenStatePreview

/** The design system's `ListPanePreview`: a wide window, for a screen rendered inside a pane (#285). */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "pane", device = FOLDABLE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrListPanePreview

/** The design system's `LandscapePanesPreview`: the narrowest window that puts two panes side by side (#814). */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "phone-land", device = PHONE_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrLandscapePanesPreview

/** A landscape tablet's window alone, past the design system's expanded-rail line, where the app shows three panes (#1110). */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "tablet-land", device = TABLET_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrTabletPanesPreview

/** A phone window at the two larger font scales, 1.5 and 2.0; the 1.0 cell is the screen's own frame. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "font15", device = PHONE_PORTRAIT_SPEC, fontScale = 1.5f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "font20", device = PHONE_PORTRAIT_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrFontScalePreviews

/** One phone cell in Spanish, whose labels run longer than English ("Unblock" is "Desbloquear"). */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "es", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class SeerrSpanishPreviews

/** A component against both themes, at the size its content asks for. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "dark", uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "light", uiMode = UI_MODE_NIGHT_NO, locale = SOURCE_LOCALE)
annotation class SeerrComponentPreviews

/** [SeerrComponentPreviews] at a phone's width and [TALL_COMPONENT_HEIGHT_DP] tall, for a section longer than the renderer's default cap. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "dark", widthDp = 411, heightDp = TALL_COMPONENT_HEIGHT_DP, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "light", widthDp = 411, heightDp = TALL_COMPONENT_HEIGHT_DP, uiMode = UI_MODE_NIGHT_NO, locale = SOURCE_LOCALE)
annotation class SeerrTallComponentPreviews

/**
 * A television surface: the design system's `TvPreviews`, on the theme's own canvas, for a component. [SeerrTvScreenPreviews]
 * paints the window's black behind it instead, for a screen root that paints no base of its own. A baseline that moves
 * between them names a surface relying on the harness to paint for it.
 */
@PreviewWrapper(SeerrTvScreenshotThemeWrapper::class)
@Preview(name = "tv", device = TV_PREVIEW_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrTvPreviews

/** The design system's `TvPreviewsOnBlack`: [SeerrTvPreviews] with the window's black painted behind it, for a screen root. */
@PreviewWrapper(SeerrTvScreenshotThemeOnBlackWrapper::class)
@Preview(name = "tv", device = TV_PREVIEW_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrTvScreenPreviews

/** [SeerrTvScreenPreviews] in Spanish, whose notes run longer than English, for a screen that must fit them without moving. */
@PreviewWrapper(SeerrTvScreenshotThemeOnBlackWrapper::class)
@Preview(name = "tv-es", device = TV_PREVIEW_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class SeerrTvSpanishScreenPreviews

/** [SeerrTvScreenPreviews] at a 1.5 font scale, for a screen whose copy has to survive a user's text size without being cut. */
@PreviewWrapper(SeerrTvScreenshotThemeOnBlackWrapper::class)
@Preview(name = "tv-font15", device = TV_PREVIEW_SPEC, fontScale = 1.5f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrTvFontScaleScreenPreviews
