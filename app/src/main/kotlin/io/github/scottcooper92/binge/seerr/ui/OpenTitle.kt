package io.github.scottcooper92.binge.seerr.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.CompanionManifest
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
 * The hand-off to Binge for a title: this app renders no title page of its own. Binge is asked by
 * the link the SDK builds ([CompanionManifest.hostTitleUri]), part of the platform rather than
 * something this app knows by sharing an author with Binge. The manifest declares its scheme under
 * `queries`, so the system says whether anything answers it; when nothing does, the server's web
 * page opens.
 */
object BingeHandOff {
    /** The link for a title, or null where there is none to build: a title with no TMDB id. */
    fun titleUri(
        mediaType: RequestMediaType,
        tmdbId: Int,
    ): String? {
        if (tmdbId <= 0) return null
        val type = if (mediaType == RequestMediaType.Tv) MediaType.MEDIA_TYPE_TV else MediaType.MEDIA_TYPE_MOVIE
        return CompanionManifest.hostTitleUri(type, tmdbId)
    }

    fun target(
        bingeAnswers: Boolean,
        mediaType: RequestMediaType,
        tmdbId: Int,
        webUrl: String,
    ): TitleTarget = titleUri(mediaType, tmdbId)?.takeIf { bingeAnswers }?.let(TitleTarget::Binge) ?: TitleTarget.Web(webUrl)
}

/** Whether Binge is installed and would answer the title hand-off link, per the manifest's `queries`. */
fun Context.bingeAnswersTitleLink(
    mediaType: RequestMediaType,
    tmdbId: Int,
): Boolean {
    val uri = BingeHandOff.titleUri(mediaType, tmdbId) ?: return false
    return Intent(Intent.ACTION_VIEW, uri.toUri()).resolveActivity(packageManager) != null
}

/** Opens a title in Binge where it is installed and answers, else at [webUrl] on the server. */
fun Context.openTitle(
    mediaType: RequestMediaType,
    tmdbId: Int,
    webUrl: String,
) {
    val answers = bingeAnswersTitleLink(mediaType, tmdbId)
    when (val target = BingeHandOff.target(answers, mediaType, tmdbId, webUrl)) {
        is TitleTarget.Binge ->
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, target.uri.toUri()))
            }.onFailure { openInBrowser(webUrl) }
        is TitleTarget.Web -> openInBrowser(target.url)
    }
}

/**
 * Opens a title in Binge on a television, which has no browser to fall back to: the caller checks
 * [bingeAnswersTitleLink] first and hides the affordance where it is false, so this only ever fires
 * where Binge is expected to answer. A launch that still fails is swallowed rather than opened
 * anywhere else.
 */
fun Context.openTitleInBinge(
    mediaType: RequestMediaType,
    tmdbId: Int,
) {
    val uri = BingeHandOff.titleUri(mediaType, tmdbId) ?: return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }
}
