package com.cooper.binge.companion.seerr.ui.settings.server

/** The server's comma-separated keyword ids. */
fun String.tagIds(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }
