package io.github.scottcooper92.binge.seerr.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCardSection
import kotlinx.coroutines.launch
import com.binge.designsystem.R as DesR

/** Debug builds only: the request detail's "This request" card and its smaller "Other requests" cards, over sample data. */
@Composable
internal fun RequestCardsPrototypeScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tapped = stringResource(R.string.proto_tapped)
    val detail = remember { cardsPrototypeDetail() }
    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = stringResource(R.string.proto_request_cards_title),
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.screenOuterPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(padding.screenInnerPadding()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            RequestCardSection(
                detail = detail,
                onOpenRequest = { id -> scope.launch { snackbar.showSnackbar(tapped.format("request $id")) } },
                onOpenUser = { scope.launch { snackbar.showSnackbar(tapped.format("user $it")) } },
            )
        }
    }
}
