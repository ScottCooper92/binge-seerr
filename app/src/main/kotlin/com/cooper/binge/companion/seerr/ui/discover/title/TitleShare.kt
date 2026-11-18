package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import android.content.Intent
import com.cooper.binge.companion.seerr.R

/** Offers the title's page on the server to another app, as text. */
internal fun Context.shareTitle(
    title: String,
    url: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    startActivity(Intent.createChooser(send, getString(R.string.title_page_action_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
