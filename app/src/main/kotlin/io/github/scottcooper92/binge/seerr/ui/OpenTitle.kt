package io.github.scottcooper92.binge.seerr.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/** Where a tapped title goes: Binge's own page for it, or the server's web page when Binge is not there. */
sealed interface TitleTarget {
    data class Binge(
        val uri: String,
    ) : TitleTarget

    data class Web(
        val url: String,
    ) : TitleTarget
}

/**
 * The hand-off to Binge for a title: this app renders no title page of its own. Binge is asked
 * by its `binge://title/<type>/<tmdbId>` link, declared under `queries` in the manifest so the
 * system says whether anything answers it; when nothing does, the server's web page opens.
 */
object BingeHandOff {
    private const val SCHEME = "binge"
    private const val HOST = "title"
    private const val TYPE_MOVIE = "movie"
    private const val TYPE_TV = "tv"

    fun titleUri(
        mediaType: RequestMediaType,
        tmdbId: Int,
    ): String = "$SCHEME://$HOST/${if (mediaType == RequestMediaType.Tv) TYPE_TV else TYPE_MOVIE}/$tmdbId"

    fun target(
        bingeAnswers: Boolean,
        mediaType: RequestMediaType,
        tmdbId: Int,
        webUrl: String,
    ): TitleTarget = if (bingeAnswers) TitleTarget.Binge(titleUri(mediaType, tmdbId)) else TitleTarget.Web(webUrl)
}

/** Opens a title in Binge where it is installed and answers, else at [webUrl] on the server. */
fun Context.openTitle(
    mediaType: RequestMediaType,
    tmdbId: Int,
    webUrl: String,
) {
    val intent = Intent(Intent.ACTION_VIEW, BingeHandOff.titleUri(mediaType, tmdbId).toUri())
    val answers = intent.resolveActivity(packageManager) != null
    when (val target = BingeHandOff.target(answers, mediaType, tmdbId, webUrl)) {
        is TitleTarget.Binge -> runCatching { startActivity(intent) }.onFailure { openInBrowser(webUrl) }
        is TitleTarget.Web -> openInBrowser(target.url)
    }
}
