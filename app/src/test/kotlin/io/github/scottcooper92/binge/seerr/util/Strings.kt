package io.github.scottcooper92.binge.seerr.util

import androidx.annotation.StringRes
import org.robolectric.RuntimeEnvironment

/** The app's string for [id] in the Robolectric application, which is what a Compose test finds a node by. */
fun string(
    @StringRes id: Int,
): String = RuntimeEnvironment.getApplication().getString(id)

/** As above, with [args] formatted in. Separate, so a string with a literal `%` in it is not formatted. */
fun string(
    @StringRes id: Int,
    vararg args: Any,
): String = RuntimeEnvironment.getApplication().getString(id, *args)
