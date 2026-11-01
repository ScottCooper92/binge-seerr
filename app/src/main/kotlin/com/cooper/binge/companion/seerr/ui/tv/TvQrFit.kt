package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/** How much of the room's width the code takes: a little under all of it, so it sits in the card rather than against it. */
private const val QR_FILL = 0.6f

/**
 * Fills the room it is given and centres [card] in it, sized so the whole card fits the height. [card] draws the
 * design system's card at the [Dp] it is handed for the code. It is composed once at the widest size, hidden from
 * semantics, to read how much height the card adds around the code, then again at the size that fits. Nothing here
 * copies the card's layout, so a change to its padding, lines or states needs no change here.
 */
@Composable
internal fun FitQrCard(
    modifier: Modifier = Modifier,
    card: @Composable (qrSize: Dp) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val widest = (width * QR_FILL).toInt()
        val probe =
            subcompose("probe") { Box(Modifier.clearAndSetSemantics { }) { card(widest.toDp()) } }
                .firstOrNull()
                ?.measure(Constraints())
        val chrome = (probe?.height ?: widest) - widest
        val fit = minOf(widest, maxOf(height - chrome, 0))
        val placeable = subcompose("fit") { card(fit.toDp()) }.firstOrNull()?.measure(Constraints())
        layout(width, height) {
            placeable?.place((width - placeable.width) / 2, (height - placeable.height) / 2)
        }
    }
}
