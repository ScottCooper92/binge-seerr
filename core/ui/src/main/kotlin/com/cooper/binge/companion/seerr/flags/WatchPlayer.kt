package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How Watch on a title in the library picks where to play it. [Automatic] is what Watch did before the choice existed: Ephyra
 * where it is installed and answers, otherwise the chooser. The rest are the user's pick: [Ask] always shows the chooser, and
 * the others open that way straight away. A pick that does not answer (Ephyra uninstalled, no link for the server) falls back to
 * [Automatic], never to an error.
 */
enum class WatchPlayer(
    /** The name stored on the device since the choice shipped. A different one would lose what users chose. */
    val key: String,
) {
    Automatic("automatic"),
    Ask("ask"),
    Ephyra("ephyra"),
    Binge("binge"),
    MediaServer("media_server"),
    ;

    companion object {
        /** The choice stored under [key]; [Automatic] where none is stored or the name is not one this build knows. */
        fun fromKey(key: String?): WatchPlayer = entries.firstOrNull { it.key == key } ?: Automatic
    }
}

/** What the title page's Watch follows. [WatchPlayer.Automatic] until a root provides the user's choice. */
val LocalWatchPlayer = staticCompositionLocalOf { WatchPlayer.Automatic }
