package com.cooper.binge.companion.seerr.ui.hubs

import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab

/** The bar's tab a hub is the root of, which is what a tap on that tab again names. */
internal fun HubTab.shellTab(): SeerrTab =
    when (this) {
        HubTab.Movies -> SeerrTab.Movies
        HubTab.Series -> SeerrTab.Tv
    }
