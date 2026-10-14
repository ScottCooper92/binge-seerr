package com.cooper.binge.companion.seerr.ui

/** PROTOTYPE: the server a TV sign-out left, so setup opens on its sign-in rather than an empty address. Not for main. */
internal object ProtoSignOut {
    @Volatile var serverUrl: String? = null
}
