package com.cooper.binge.companion.seerr.ui.tv

import android.content.res.Configuration

/**
 * Whether this configuration is a television's. The one runtime check that selects the TV shell, read
 * off the UI mode as Binge reads it, so the two apps agree on what a television is.
 */
internal fun Configuration.isTelevision(): Boolean = uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
