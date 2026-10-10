package io.github.scottcooper92.binge.seerr.ui

/**
 * The instant every frame that shows relative dates is drawn at. It is fixed, and far past anything a sample's own dates
 * are measured from, so a date lands past the relative-date window and renders as the same absolute date whenever the
 * suite runs: a "now" taken from the clock made the committed frames drift a day on every CI run after they were
 * recorded. Shared, so one frame cannot differ from its sibling because one fixture moved and the other did not.
 */
internal const val FIXED_NOW_MILLIS = 1_770_000_000_000L

/** The address hand-off code the TV setup frames show for a phone to scan. */
internal val HAND_OFF_CODE = AddressHandOff.Listening(url = "http://192.168.86.53:41234/a/k7m2pqx4", pin = "4821")
