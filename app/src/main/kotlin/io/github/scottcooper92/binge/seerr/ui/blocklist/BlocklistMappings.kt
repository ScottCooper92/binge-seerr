package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.annotation.StringRes
import io.github.scottcooper92.binge.seerr.R

@StringRes
internal fun BlocklistFilter.labelRes(): Int =
    when (this) {
        BlocklistFilter.All -> R.string.blocklist_filter_all
        BlocklistFilter.Manual -> R.string.blocklist_filter_manual
        BlocklistFilter.Tagged -> R.string.blocklist_filter_tagged
    }
