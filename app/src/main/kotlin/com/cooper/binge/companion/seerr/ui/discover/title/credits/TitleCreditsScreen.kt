package com.cooper.binge.companion.seerr.ui.discover.title.credits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.TitleCredits
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** The whole cast, then the crew by department. A person is not tappable until the person page exists. */
@Composable
internal fun TitleCreditsScreen(
    state: TitleCreditsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(
        title = stringResource(R.string.title_credits_title),
        modifier = modifier,
        bar = ScreenBar.Small,
        onBack = onBack,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                TitleCreditsUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is TitleCreditsUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry)
                is TitleCreditsUiState.Ready -> CreditsList(state.credits, inner)
            }
        }
    }
}

@Composable
private fun CreditsList(
    credits: TitleCredits,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        if (credits.cast.isNotEmpty()) {
            item(key = "cast") { SectionHeader(stringResource(R.string.title_credits_cast)) }
            items(credits.cast, key = { "cast-${it.personId}" }) { CreditRow(it) }
        }
        credits.departments.forEach { department ->
            item(key = "dept-${department.name}") { SectionHeader(department.name.departmentLabel()) }
            items(department.people, key = { "${department.name}-${it.personId}" }) { CreditRow(it) }
        }
    }
}

/** A person with an avatar, initials where there is no photo, and their character or jobs under the name. */
@Composable
private fun CreditRow(person: TitlePerson) {
    ListRow(
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
        leading = { BingeInitialsAvatar(name = person.name, avatarUrl = person.photoUrl) },
    ) { contentModifier ->
        Column(modifier = contentModifier.padding(vertical = dimensionResource(DesR.dimen.padding_xs))) {
            ListRowHeader(title = person.name)
            person.role?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = ROLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val ROLE_LINES = 2
