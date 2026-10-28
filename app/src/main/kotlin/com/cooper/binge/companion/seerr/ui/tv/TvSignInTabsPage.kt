package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.nav.LocalTvTopInset
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * The sign-in step as a television shows it: the server's name over a row of tabs, one for each way in with the code
 * first, then what the chosen tab needs in a centred column, and a control pinned along the bottom edge.
 * There is no copy pane; the tabs say what the choices are.
 */
@Composable
internal fun TvSignInTabsPage(
    headline: String,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gap = dimensionResource(TvR.dimen.tv_two_pane_action_gap)
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                        end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                        top = dimensionResource(TvR.dimen.tv_overscan_vertical) + LocalTvTopInset.current,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { tabs() }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = gap * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
        footer?.let { TvSignInFooter(it) }
    }
}

/** A control pinned where it is on every tab, centred along the bottom edge, with no bar around it. */
@Composable
private fun TvSignInFooter(footer: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = dimensionResource(DesR.dimen.padding_m),
                    bottom = dimensionResource(TvR.dimen.tv_overscan_vertical),
                ),
        contentAlignment = Alignment.Center,
    ) { footer() }
}
