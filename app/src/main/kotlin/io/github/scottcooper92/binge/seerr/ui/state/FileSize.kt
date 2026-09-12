package io.github.scottcooper92.binge.seerr.ui.state

import java.util.Locale

private const val BYTES_PER_UNIT = 1024.0
private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

/** `4.2 GB`: one decimal from megabytes up, none below. */
fun formatFileSize(bytes: Long): String {
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= BYTES_PER_UNIT && unit < UNITS.lastIndex) {
        value /= BYTES_PER_UNIT
        unit++
    }
    val pattern = if (unit >= 2) "%.1f %s" else "%.0f %s"
    return String.format(Locale.getDefault(), pattern, value, UNITS[unit])
}
