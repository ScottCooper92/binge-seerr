package io.github.scottcooper92.binge.seerr.ui

/**
 * The instant every frame that shows relative dates is drawn at. It is fixed, so the rendered text is the same on every
 * run: a "now" taken from the clock made the committed frames drift a day on every CI run after they were recorded.
 * Shared, so one frame cannot differ from its sibling because one fixture moved and the other did not. A sample whose
 * date should read as an absolute date keeps its own timestamp far before this one.
 */
internal const val FIXED_NOW_MILLIS = 1_770_000_000_000L

/** The address hand-off code the TV setup frames show for a phone to scan. */
internal val HAND_OFF_CODE = AddressHandOff.Listening(url = "http://192.168.86.53:41234/a/k7m2pqx4", pin = "4821")
