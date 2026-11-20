package com.cooper.binge.companion.seerr.ui.settings.server

/** The server's comma-separated keyword ids. */
internal fun String.tagIds(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }
