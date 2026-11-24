package com.cooper.binge.companion.seerr.ui

/** A sign-in that finishes somewhere else: the user approves a code, and the form waits. */
sealed interface LinkFlow {
    val code: String

    /** [launchPending] is true until the screen has opened [authUrl] once; it is not reopened on recomposition. */
    data class Plex(
        override val code: String,
        val authUrl: String,
        val launchPending: Boolean,
    ) : LinkFlow

    data class QuickConnect(
        override val code: String,
    ) : LinkFlow
}
