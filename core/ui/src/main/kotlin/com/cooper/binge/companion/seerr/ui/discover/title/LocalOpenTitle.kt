package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.compositionLocalOf
import com.cooper.binge.companion.seerr.discover.TitleKind

/**
 * How a card opens a title: its kind, its TMDB id and its page on the server, which the flag-off fallback opens. The nav host
 * provides the real one: the title's page here with the discovery flag on, and the hand-off to Binge or the server's page
 * without it. The default does nothing, so a screen composed without a host (a frame) opens nowhere.
 */
val LocalOpenTitle = compositionLocalOf<(TitleKind, Int, String) -> Unit> { { _, _, _ -> } }
