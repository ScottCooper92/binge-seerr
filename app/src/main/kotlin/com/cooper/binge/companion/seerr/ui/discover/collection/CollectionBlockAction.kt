package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.theme.BingeTheme
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/** The page's overflow in the bar: block, or unblock, the whole collection. Only shown to a viewer who may. */
@Composable
internal fun CollectionOverflowAction(
    glassBackgroundAlpha: Float,
    blocked: Boolean,
    enabled: Boolean,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ExpressiveIconButton(
            onClick = { expanded = true },
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.overflow_menu),
            tint = lerp(BingeTheme.colors.onScrim, MaterialTheme.colorScheme.onBackground, 1f - glassBackgroundAlpha),
            tone = IconButtonTone.Glass,
            size = dimensionResource(DesR.dimen.top_bar_icon_size),
            glassBackgroundAlpha = glassBackgroundAlpha,
            enabled = enabled,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (blocked) R.string.collection_page_unblock else R.string.collection_page_block)) },
                leadingIcon = { Icon(if (blocked) Icons.AutoMirrored.Filled.Undo else Icons.Filled.Block, contentDescription = null) },
                onClick = {
                    expanded = false
                    if (blocked) onUnblock() else onBlock()
                },
            )
        }
    }
}

/** Blocking stops every film in the collection being requested, so it asks first. */
@Composable
internal fun CollectionBlockConfirm(
    name: String,
    reachesHeldFilms: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeConfirmDialog(
        title = stringResource(R.string.collection_page_block_title, name),
        message =
            stringResource(R.string.collection_page_block_message) +
                if (reachesHeldFilms) " " + stringResource(R.string.collection_page_block_held) else "",
        confirmLabel = stringResource(R.string.collection_page_block),
        destructive = true,
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
    )
}
