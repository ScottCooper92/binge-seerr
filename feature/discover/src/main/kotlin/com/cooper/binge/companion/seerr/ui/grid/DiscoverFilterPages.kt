package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeSheetDockingHeader
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.BingeSheetTopBar
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.LocalBingeSheetDock
import com.binge.designsystem.component.bingeSheetPinnedFooter
import com.binge.designsystem.component.bingeSheetScroll
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.cleared
import com.cooper.binge.companion.seerr.feature.discover.R
import com.binge.designsystem.R as DesR

/** The page a filter row opens over the sheet: its title with Clear, the control, and Done. */
@Composable
fun ColumnScope.DiscoverSectionPage(
    section: DiscoverFilterSection,
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    loads: FilterLoads,
    onChange: (DiscoverFilters) -> Unit,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val inset = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))
    Column(
        modifier = Modifier.weight(1f, fill = false).bingeSheetScroll(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        val cleared = filters.cleared(section.group)
        SectionPageHeader(
            stringResource(section.title),
            onBack = onBack,
            clearEnabled = filters != cleared,
            onClear = { onChange(cleared) },
        )
        when (section) {
            DiscoverFilterSection.Genres -> Unit
            DiscoverFilterSection.Language -> Unit
            DiscoverFilterSection.Release -> Unit
            DiscoverFilterSection.Rating -> Unit
            DiscoverFilterSection.Runtime -> Unit
            DiscoverFilterSection.Votes -> Unit
            DiscoverFilterSection.WhereToWatch -> WherePage(filters, vocabulary, inset, loads, onChange)
            DiscoverFilterSection.Certification -> Unit
            DiscoverFilterSection.Keywords ->
                KeywordsPage(filters.keywords, vocabulary, inset, loads, exclude = false) { onChange(filters.copy(keywords = it)) }
            DiscoverFilterSection.ExcludeKeywords ->
                KeywordsPage(
                    filters.excludeKeywords,
                    vocabulary,
                    inset,
                    loads,
                    exclude = true,
                ) { onChange(filters.copy(excludeKeywords = it)) }
            DiscoverFilterSection.Status -> StatusPage(filters, inset, onChange)
        }
    }
    BingeSheetFooter {
        // A docking sheet lays its page out at full height and slides it, so the footer is lifted onto the window's foot while the
        // sheet is part-way open, as the filter sheet's is.
        val dock = LocalBingeSheetDock.current
        Column(modifier = Modifier.bingeSheetPinnedFooter(dock, MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (dock != null) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionPageFooter(applied = filters != filters.cleared(section.group), onDone = onDone)
        }
    }
}

/**
 * A page's heading: the large title while the sheet floats and the sheet's own bar once it docks, so the title is on screen
 * once, whichever the sheet is doing. It carries the sheet's drag handle. A page of a bottom sheet has no Back bar of the
 * side sheet's to lean on, so it passes [onBack]: the floating title gets an arrow, and the docked bar's close goes back a
 * page instead of closing the sheet. A page with no Clear passes none.
 */
@Composable
fun SectionPageHeader(
    title: String,
    onClear: (() -> Unit)? = null,
    clearEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    BingeSheetDockingHeader(
        header = {
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(
                        start = dimensionResource(if (onBack == null) DesR.dimen.padding_m else DesR.dimen.padding_xs),
                        top = dimensionResource(DesR.dimen.padding_m),
                        end = dimensionResource(DesR.dimen.padding_s),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(DesR.string.cd_navigate_back),
                        )
                    }
                }
                Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (onClear != null) {
                    BingeTextButton(label = stringResource(R.string.grid_filters_clear), onClick = onClear, enabled = clearEnabled)
                }
            }
        },
        dockedTopBar = { onClose ->
            BingeSheetTopBar(
                title = title,
                onClose = onBack ?: onClose,
                actions = {
                    if (onClear != null) {
                        BingeTextButton(
                            label = stringResource(R.string.grid_filters_clear),
                            onClick = onClear,
                            enabled = clearEnabled,
                        )
                    }
                },
            )
        },
    )
}

/** The footer of a section page: one button, Apply once the page has a selection and Close while it has none. */
@Composable
private fun SectionPageFooter(
    applied: Boolean,
    onDone: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(top = dimensionResource(DesR.dimen.padding_sm), bottom = dimensionResource(DesR.dimen.padding_l)),
    ) {
        BingeFilledButton(
            label = stringResource(if (applied) R.string.grid_filters_apply else R.string.grid_filters_close),
            onClick = onDone,
            modifier = Modifier.weight(1f),
        )
    }
}

fun Set<Int>.toggled(id: Int): Set<Int> = if (id in this) this - id else this + id
