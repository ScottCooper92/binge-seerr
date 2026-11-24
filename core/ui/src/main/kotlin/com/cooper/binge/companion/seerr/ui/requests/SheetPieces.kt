package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.theme.BingeSentiment

/** The title's combined state as a chip: its words and the tone of the status chip the cards draw. */
class SheetStateChip(
    val label: String,
    val sentiment: BingeSentiment,
)

/** What a sheet over the title page holds: the title's own rows and media actions, one request's own, or both together. */
enum class SheetScope { Both, Title, Request }

/** One row the title page adds to a request's sheet. An [external] row leaves the app, and any other opens something in it. */
class SheetTitleRow(
    val icon: ImageVector,
    val label: String,
    val external: Boolean,
    val onClick: () -> Unit,
)
