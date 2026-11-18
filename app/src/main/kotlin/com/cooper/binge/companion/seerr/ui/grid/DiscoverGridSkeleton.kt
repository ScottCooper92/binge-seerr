package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.MediaCardSkeleton
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.mediaGridColumns
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

private const val SKELETON_ROWS = 8

/**
 * The grid's frame, shared by the loaded grid and its skeleton so the columns, the gaps and the padding are one thing:
 * a skeleton that drew its own would agree with the grid only until one of them changed.
 */
@Composable
internal fun GridFrame(
    contentPadding: PaddingValues,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(mediaGridColumns()),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        content = content,
    )
}

/** Marks the grid's first cell, loaded or not, so the geometry test can check that the first title lands where its plate stood. */
internal fun Modifier.firstCellAnchor(index: Int): Modifier =
    if (index == 0) layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)) else this

/** What the grid shows while its first page loads: poster plates at the grid's column count and cell size. */
@Composable
internal fun GridSkeleton(contentPadding: PaddingValues) {
    val loading = stringResource(R.string.grid_loading)
    val cells = mediaGridColumns() * SKELETON_ROWS
    GridFrame(contentPadding) {
        items(cells) { index ->
            MediaCardSkeleton(Modifier.firstCellAnchor(index), description = if (index == 0) loading else null)
        }
    }
}
