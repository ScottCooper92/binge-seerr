package io.github.scottcooper92.binge.seerr.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.binge.designsystem.preview.ScreenshotThemeWrapper

/**
 * The declared default locale, pinned on every frame.
 *
 * Without it these baselines record the **en-XA pseudolocale**: the debug variant generates it, and
 * `values-en-XA` is a closer match for a bare `en` — or for no request at all — than the untagged
 * default folder, so the renderer resolves to it and every string comes out accented and bracketed.
 *
 * Binge never meets this. It enables pseudolocales in its *application* plugin and keeps every frame
 * in a library module, so the two never share a variant. This app is one module and they do, which
 * is why the matrix below is spelled out here rather than taken from the design system.
 */
private const val DEFAULT_LOCALE = "en-rGB"

/**
 * The device matrix for a screen's canonical layout frame, mirroring the design system's
 * `@ScreenPreviews` cell for cell and adding [DEFAULT_LOCALE].
 *
 * | cell          | window        | theme |
 * |---------------|---------------|-------|
 * | `phone`       | 411×891 port  | dark  |
 * | `phone-light` | 411×891 port  | light |
 * | `phone-land`  | 891×411 land  | dark  |
 * | `foldable`    | 840×1180 port | dark  |
 * | `tablet`      | 1280×800 land | dark  |
 */
@PreviewWrapper(ScreenshotThemeWrapper::class)
@Preview(name = "phone", device = "spec:width=411dp,height=891dp,orientation=portrait", uiMode = UI_MODE_NIGHT_YES, locale = DEFAULT_LOCALE)
@Preview(
    name = "phone-light",
    device = "spec:width=411dp,height=891dp,orientation=portrait",
    uiMode = UI_MODE_NIGHT_NO,
    locale = DEFAULT_LOCALE,
)
@Preview(
    name = "phone-land",
    device = "spec:width=411dp,height=891dp,orientation=landscape",
    uiMode = UI_MODE_NIGHT_YES,
    locale = DEFAULT_LOCALE,
)
@Preview(
    name = "foldable",
    device = "spec:width=840dp,height=1180dp,orientation=portrait",
    uiMode = UI_MODE_NIGHT_YES,
    locale = DEFAULT_LOCALE,
)
@Preview(
    name = "tablet",
    device = "spec:width=800dp,height=1280dp,orientation=landscape",
    uiMode = UI_MODE_NIGHT_YES,
    locale = DEFAULT_LOCALE,
)
annotation class SeerrScreenPreviews

/**
 * One cell — [SeerrScreenPreviews]' `phone` cell, byte-for-byte the same spec — for a **state** of a
 * screen whose layout frame already carries the full matrix.
 *
 * The matrix answers a layout question, asked once per layout. A loading skeleton or an empty arm
 * changes content within that layout, and its foldable and tablet cells re-answer a settled question
 * at the two most expensive render sizes in the suite.
 */
@PreviewWrapper(ScreenshotThemeWrapper::class)
@Preview(name = "phone", device = "spec:width=411dp,height=891dp,orientation=portrait", uiMode = UI_MODE_NIGHT_YES, locale = DEFAULT_LOCALE)
annotation class SeerrScreenStatePreview

/**
 * One cell on a **wide** window, for a screen rendered inside a pane rather than across the window.
 *
 * The window is what a resource qualifier resolves against, so this is the only cell where a pane's
 * own width and the qualifier's answer disagree — which is the bug it exists to hold (#285). The
 * frame constrains the composable to the pane's width itself; this only supplies the window.
 */
@PreviewWrapper(ScreenshotThemeWrapper::class)
@Preview(
    name = "pane",
    device = "spec:width=840dp,height=1180dp,orientation=portrait",
    uiMode = UI_MODE_NIGHT_YES,
    locale = DEFAULT_LOCALE,
)
annotation class SeerrListPanePreview

/** A component against both themes, at the size its content asks for. */
@PreviewWrapper(ScreenshotThemeWrapper::class)
@Preview(name = "dark", uiMode = UI_MODE_NIGHT_YES, locale = DEFAULT_LOCALE)
@Preview(name = "light", uiMode = UI_MODE_NIGHT_NO, locale = DEFAULT_LOCALE)
annotation class SeerrComponentPreviews
