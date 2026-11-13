package com.cooper.binge.companion.seerr.ui.settings.licences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

private const val LICENCE_DETAIL_MAX_LINES = 2

/** Rows per group: the page is lazy by group, so a few hundred libraries are never all composed at once. */
internal const val LICENCE_GROUP_SIZE = 12

/** The open-source licences page: every library the app ships, with its version and licence, linking to its site. */
@Composable
internal fun LicencesEntry(
    onBack: () -> Unit,
    viewModel: LicencesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LicencesScreen(state = state, onBack = onBack, onOpenWebsite = context::openInBrowser)
}

@Composable
internal fun LicencesScreen(
    state: LicencesUiState,
    onBack: () -> Unit,
    onOpenWebsite: (String) -> Unit,
) {
    BingeScreenScaffold(bar = ScreenBar.Small, title = stringResource(R.string.licences_title), onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                LicencesUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                LicencesUiState.Unreadable -> EmptyScreen(stringResource(R.string.licences_unreadable), Modifier.padding(inner))
                is LicencesUiState.Ready ->
                    if (state.entries.isEmpty()) {
                        EmptyScreen(stringResource(R.string.licences_empty), Modifier.padding(inner))
                    } else {
                        LicenceList(state.entries, inner, onOpenWebsite)
                    }
            }
        }
    }
}

@Composable
private fun LicenceList(
    entries: List<LicenceEntry>,
    contentPadding: PaddingValues,
    onOpenWebsite: (String) -> Unit,
) {
    val unspecified = stringResource(R.string.licences_unspecified)
    val sides = resolvedContentPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        itemsIndexed(entries.chunked(LICENCE_GROUP_SIZE), key = { index, _ -> index }) { _, group ->
            ItemGroup(title = null, rows = group.map { it.toRow(unspecified, onOpenWebsite) }, modifier = Modifier.padding(sides))
        }
    }
}

@Composable
private fun LicenceEntry.toRow(
    unspecified: String,
    onOpenWebsite: (String) -> Unit,
): ListItem {
    val licenceNames = licences.joinToString().ifEmpty { unspecified }
    val link = website
    return ListItem(
        icon = Icons.Filled.Description,
        iconTint = BingeSentiment.Neutral.fill(),
        label = name,
        detail = version?.let { stringResource(R.string.licences_detail_format, it, licenceNames) } ?: licenceNames,
        // The licence names are what the row is for, so they may wrap rather than lose their end.
        supportingMaxLines = LICENCE_DETAIL_MAX_LINES,
        clickable = link != null,
        destination = ListItemDestination.External,
        onClick = { link?.let(onOpenWebsite) },
    )
}
