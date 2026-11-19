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
 * Fewer cells than the shared set, on purpose. A screen's layout frame is [SeerrScreenPreviews], the `phone` cell alone,
 * and a section root adds `phone-light` and `phone-land` through [SeerrRootScreenPreviews], so a root's own chrome is
 * seen in the light theme. `foldable` is not a cell here: it showed what `phone-land` shows. The two-pane layout past
 * the expanded-rail line is [SeerrTabletPanesPreview], on the frames that compose the panes. Add a cell back when a
 * screen gains a layout the cells above do not show.
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

/** The frame that establishes a screen's layout: the design system's `phone` cell. A state of the screen takes [SeerrScreenStatePreview]. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrScreenPreviews

/** A section root's layout frame: `phone`, `phone-light` for the root's own chrome, and `phone-land`, the narrowest window that puts two panes side by side. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "phone-light", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_NO, locale = SOURCE_LOCALE)
@Preview(name = "phone-land", device = PHONE_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrRootScreenPreviews

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

/** A landscape tablet's window alone, past the design system's expanded-rail line, where the app shows the hub beside one content pane (#2348). */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "tablet-land", device = TABLET_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrTabletPanesPreview

/** A phone window at font scale 1.3, where a phone's own "larger text" setting commonly lands, between the 1.0 cell and the 2.0 one. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "font13", device = PHONE_PORTRAIT_SPEC, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrFontScale13Previews

/** A phone window at font scale 2.0, the largest a user can set; the 1.0 cell is the screen's own frame and [SeerrFontScale13Previews] the middle. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "font20", device = PHONE_PORTRAIT_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrFontScalePreviews

/** A component at a phone's width and font scale 1.3, the size a phone's own "larger text" setting commonly lands on, between the 1.0 and 2.0 cells. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "font13-dark", widthDp = 411, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "font13-light", widthDp = 411, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_NO, locale = SOURCE_LOCALE)
annotation class SeerrFontScale13ComponentPreviews

/** One phone cell in Spanish, whose labels run longer than English ("Unblock" is "Desbloquear"). */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "es", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class SeerrSpanishPreviews

/** A portrait tablet at 800dp: wider than the hero template's 640dp column, narrower than the expanded line, so the page keeps one column. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(
    name = "tablet-port",
    device = "spec:width=800dp,height=1280dp,orientation=portrait",
    uiMode = UI_MODE_NIGHT_YES,
    locale = SOURCE_LOCALE,
)
annotation class SeerrNarrowTabletPreview

/** The expanded windows in Spanish, where the cinematic header's overlay copy runs longer than English. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "foldable-es", device = FOLDABLE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
@Preview(name = "tablet-es", device = TABLET_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class SeerrExpandedSpanishPreviews

/** The expanded windows at font scales 1.3 and 2.0, where the header's overlay grows and the reading column must still hold. */
@PreviewWrapper(SeerrScreenshotThemeWrapper::class)
@Preview(name = "foldable-font13", device = FOLDABLE_PORTRAIT_SPEC, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "foldable-font20", device = FOLDABLE_PORTRAIT_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "tablet-font13", device = TABLET_LANDSCAPE_SPEC, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "tablet-font20", device = TABLET_LANDSCAPE_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class SeerrExpandedFontScalePreviews

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
