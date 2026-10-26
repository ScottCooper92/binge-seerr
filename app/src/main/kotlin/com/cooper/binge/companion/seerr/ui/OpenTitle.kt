package com.cooper.binge.companion.seerr.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.binge.companion.sdk.CompanionManifest
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.mediaIdOf
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType

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
        val id = mediaIdOf(mediaType, tmdbId) ?: return null
        return CompanionManifest.hostTitleUri(id.mediaType, id.tmdbId)
    }

    fun target(
        bingeAnswers: Boolean,
        mediaType: RequestMediaType,
        tmdbId: Int,
        webUrl: String,
    ): TitleTarget = titleUri(mediaType, tmdbId)?.takeIf { bingeAnswers }?.let(TitleTarget::Binge) ?: TitleTarget.Web(webUrl)
}

/**
 * Whether Binge is installed and would answer the title hand-off link, per the manifest's `queries`. False
 * while [promoted] is false, so the title opens on the server's own page.
 */
fun Context.bingeAnswersTitleLink(
    promoted: Boolean,
    mediaType: RequestMediaType,
    tmdbId: Int,
): Boolean {
    if (!promoted) return false
    val uri = BingeHandOff.titleUri(mediaType, tmdbId) ?: return false
    return Intent(Intent.ACTION_VIEW, uri.toUri()).resolveActivity(packageManager) != null
}

/** Opens a title in Binge where it is installed and answers, else at [webUrl] on the server. */
fun Context.openTitle(
    promoted: Boolean,
    mediaType: RequestMediaType,
    tmdbId: Int,
    webUrl: String,
) {
    val answers = bingeAnswersTitleLink(promoted, mediaType, tmdbId)
    when (val target = BingeHandOff.target(answers, mediaType, tmdbId, webUrl)) {
        is TitleTarget.Binge ->
            attempt {
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
    attempt { startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }
}

/** Opening a title in Binge on a television, or null where Binge would not answer: the caller hides the affordance then. */
fun Context.bingeTitleHandOff(
    promoted: Boolean,
    mediaType: RequestMediaType,
    tmdbId: Int,
): (() -> Unit)? = { openTitleInBinge(mediaType, tmdbId) }.takeIf { bingeAnswersTitleLink(promoted, mediaType, tmdbId) }
