package io.github.scottcooper92.binge.seerr.ui.hub

import android.content.Context

/** Whether release Binge is installed right now, behind a seam so [HubViewModel] is testable without a real [Context]. */
fun interface BingeInstallCheck {
    fun isInstalled(): Boolean
}

class PackageManagerBingeInstallCheck(
    private val context: Context,
) : BingeInstallCheck {
    override fun isInstalled(): Boolean = context.isBingeInstalled()
}

/** Always reports not installed — the default for a test or preview that has not wired one. */
object NoBingeInstallCheck : BingeInstallCheck {
    override fun isInstalled(): Boolean = false
}
