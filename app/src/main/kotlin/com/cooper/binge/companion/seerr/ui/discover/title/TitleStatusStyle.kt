package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.annotation.StringRes
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability

/** How a title's status reads: its label and its colour. */
internal data class StatusStyle(
    @StringRes val label: Int,
    val sentiment: BingeSentiment,
)

/**
 * The one place a status gets its label and colour, so a poster, a hero, a search row, a credit and the title page's primary say the
 * same thing. Null where the server holds nothing of the title: it shows no chip.
 */
internal fun TitleAvailability.statusStyle(): StatusStyle? =
    when (this) {
        TitleAvailability.None -> null
        TitleAvailability.Pending -> StatusStyle(R.string.hubs_card_requested, BingeSentiment.Caution)
        TitleAvailability.Processing -> StatusStyle(R.string.hubs_card_processing, BingeSentiment.Info)
        TitleAvailability.PartiallyAvailable -> StatusStyle(R.string.hubs_card_partial, BingeSentiment.Info)
        TitleAvailability.Available -> StatusStyle(R.string.hubs_card_available, BingeSentiment.Positive)
        TitleAvailability.Blocklisted -> StatusStyle(R.string.hubs_card_blocklisted, BingeSentiment.Negative)
    }

/** The title page primary's state as the availability it stands for; a title not yet requested is [TitleAvailability.None]. */
internal fun TitleState.toAvailability(): TitleAvailability =
    when (this) {
        TitleState.Request -> TitleAvailability.None
        TitleState.Requested -> TitleAvailability.Pending
        TitleState.Processing -> TitleAvailability.Processing
        TitleState.PartiallyAvailable -> TitleAvailability.PartiallyAvailable
        TitleState.Available -> TitleAvailability.Available
        TitleState.Blocklisted -> TitleAvailability.Blocklisted
    }
