package io.github.scottcooper92.binge.seerr.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * How a bottom sheet's body looks: a phone's width on the sheet surface. A frame renders the stateless body rather than
 * the modal sheet, which a screenshot cannot capture. Every sheet frame draws through this one, so they cannot disagree
 * about what a sheet is. [modifier] adds to it, for a sheet a frame fixes the height of.
 */
@Composable
internal fun SheetFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(Modifier.width(PHONE_WIDTH).then(modifier).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        content()
    }
}
