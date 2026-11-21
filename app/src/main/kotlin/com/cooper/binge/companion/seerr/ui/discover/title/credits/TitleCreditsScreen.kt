package com.cooper.binge.companion.seerr.ui.discover.title.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.PeopleGridScreen
import com.binge.designsystem.template.PeopleGridSection
import com.binge.designsystem.template.PeopleGridState
import com.binge.designsystem.template.PeopleGridTab
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen

/**
 * The whole cast and the crew by department, on tabs, each a grid of portrait cards with a fast scrollbar. A person opens their page.
 * The tab is the screen's own, so it survives a rotation. [initialTab] is for a frame that shows the crew.
 */
@Composable
internal fun TitleCreditsScreen(
    state: TitleCreditsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
) {
    if (state is TitleCreditsUiState.Error) {
        CreditsFailed(state, onBack, onRetry, modifier)
        return
    }
    val openTitle = LocalOpenTitle.current
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val credits = (state as? TitleCreditsUiState.Ready)?.credits
    val departmentTitles = credits?.departments.orEmpty().map { it.name.departmentLabel() }
    val grid = remember(credits, departmentTitles) { credits?.let { creditsGrid(it, departmentTitles) } }
    val castEmpty = stringResource(R.string.title_credits_cast_empty)
    val crewEmpty = stringResource(R.string.title_credits_crew_empty)
    val castLabel = stringResource(R.string.title_credits_cast)
    val crewLabel = stringResource(R.string.title_credits_crew)
    PeopleGridScreen(
        title = stringResource(R.string.title_credits_title),
        tabs =
            listOf(
                PeopleGridTab(castLabel, grid.stateOf(castEmpty) { it.cast }),
                PeopleGridTab(crewLabel, grid.stateOf(crewEmpty) { it.crew }, sectionBubble = true),
            ),
        selectedTab = selectedTab,
        onTabChange = { selectedTab = it },
        onOpenPerson = { key -> grid?.personId(key)?.let { openTitle(TitleKind.Person, it, "") } },
        onRetry = { onRetry() },
        modifier = modifier,
        onBack = onBack,
        loadingLabel = stringResource(R.string.title_credits_loading),
    )
}

/** What one tab shows: the loading plates before the credits are known, then its sections, or [emptyMessage] when it has none. */
private fun CreditsGrid?.stateOf(
    emptyMessage: String,
    sections: (CreditsGrid) -> List<PeopleGridSection>,
): PeopleGridState =
    when (val shown = this?.let(sections)) {
        null -> PeopleGridState.Loading
        else -> if (shown.isEmpty()) PeopleGridState.Empty(emptyMessage) else PeopleGridState.Ready(shown)
    }

/** A failed read stays an error screen with Try again, which also offers to allow the local network where that is the cause. */
@Composable
private fun CreditsFailed(
    state: TitleCreditsUiState.Error,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    BingeScreenScaffold(
        title = stringResource(R.string.title_credits_title),
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            ErrorScreen(state.error, Modifier.padding(padding.screenInnerPadding()), onRetry)
        }
    }
}
