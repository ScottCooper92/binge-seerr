package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDownload
import io.github.scottcooper92.binge.seerr.ui.tv.TvAdvancedRequestActions
import io.github.scottcooper92.binge.seerr.ui.tv.request

/**
 * The instant every frame that shows relative dates is drawn at. It is fixed, so the rendered text is the same on every
 * run: a "now" taken from the clock made the committed frames drift a day on every CI run after they were recorded.
 * Shared, so one frame cannot differ from its sibling because one fixture moved and the other did not. A sample whose
 * date should read as an absolute date keeps its own timestamp far before this one.
 */
internal const val FIXED_NOW_MILLIS = 1_770_000_000_000L

/** The address hand-off code the TV setup frames show for a phone to scan. */
internal val HAND_OFF_CODE = AddressHandOff.Listening(url = "http://192.168.86.53:41234/a/k7m2pqx4", pin = "4821")

/** The television advanced-request screen's actions, none of which does anything: a frame is a still. */
internal val NoAdvancedActions = TvAdvancedRequestActions({}, {}, {}, {}, {}, {})

/**
 * The three requests the television frames share, drawn at [FIXED_NOW_MILLIS]: one pending, one approved and downloading,
 * one declined. A frame that needs more adds to them rather than writing its own list.
 */
internal val FixedSampleRequests =
    listOf(
        request(1, "Heat", SeerrRequestStatusCode.Pending, now = FIXED_NOW_MILLIS),
        request(
            2,
            "The Bear",
            SeerrRequestStatusCode.Approved,
            seasons = listOf(1, 2),
            mediaStatus = SeerrMediaStatusCode.Processing,
            download = RequestDownload(0.4f, 12, true),
            now = FIXED_NOW_MILLIS,
        ),
        request(3, "Dune: Part Two", SeerrRequestStatusCode.Declined, now = FIXED_NOW_MILLIS),
    )
