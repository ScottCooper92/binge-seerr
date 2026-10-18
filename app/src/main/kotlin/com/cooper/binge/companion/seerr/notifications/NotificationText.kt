package com.cooper.binge.companion.seerr.notifications

import android.content.Context
import com.cooper.binge.companion.seerr.R

/** The words on a notice, seamed off Android so what is forwarded is tested without a Context. */
interface NotificationText {
    /** A batch's title, counting what arrived. */
    fun title(
        signal: NotificationSignal,
        count: Int,
    ): String

    /** "Heat" for one, "Heat and 2 more" for several, null when no title resolved. */
    fun body(
        titles: List<String>,
        count: Int,
    ): String?

    fun connectionTitle(): String

    fun connectionBody(): String
}

class AndroidNotificationText(
    private val context: Context,
) : NotificationText {
    override fun title(
        signal: NotificationSignal,
        count: Int,
    ): String = context.resources.getQuantityString(signal.titleRes, count, count)

    override fun body(
        titles: List<String>,
        count: Int,
    ): String? =
        summaryLine(titles, count) { first, others ->
            context.resources.getQuantityString(R.plurals.notif_and_more, others, first, others)
        }

    override fun connectionTitle(): String = context.getString(R.string.notif_connection_title)

    override fun connectionBody(): String = context.getString(R.string.notif_connection_body)
}
