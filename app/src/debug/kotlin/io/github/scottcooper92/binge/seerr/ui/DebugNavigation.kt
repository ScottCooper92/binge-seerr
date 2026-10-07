package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.debug.BingeHintPrototypeScreen
import io.github.scottcooper92.binge.seerr.ui.debug.ManageSheetPrototypeScreen
import io.github.scottcooper92.binge.seerr.ui.debug.RequestCardsPrototypeScreen
import io.github.scottcooper92.binge.seerr.ui.hub.DeveloperRow

/** The hub's developer section, present only in a debug build: each prototype opens straight off the hub. */
internal fun debugDeveloperRows(backStack: NavBackStack<NavKey>): List<DeveloperRow> =
    listOf(
        DeveloperRow(Icons.Filled.ViewAgenda, R.string.proto_manage_sheet_title, R.string.proto_manage_sheet_detail) {
            backStack.add(ManageSheetPrototypeRoute)
        },
        DeveloperRow(Icons.Filled.Lightbulb, R.string.proto_binge_hint_title, R.string.proto_binge_hint_detail) {
            backStack.add(BingeHintPrototypeRoute)
        },
        DeveloperRow(Icons.Filled.ViewAgenda, R.string.proto_request_cards_title, R.string.proto_request_cards_detail) {
            backStack.add(RequestCardsPrototypeRoute)
        },
    )

/** The prototypes' screens. [showBack] is unused here: they are always stacked above the hub, so their arrow always shows. */
internal fun EntryProviderScope<NavKey>.debugDetailEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) {
    entry<ManageSheetPrototypeRoute>(metadata = DetailPane) {
        DetailPaneContent { ManageSheetPrototypeScreen(onBack = { backStack.removeLastOrNull() }) }
    }
    entry<BingeHintPrototypeRoute>(metadata = DetailPane) {
        DetailPaneContent { BingeHintPrototypeScreen(onBack = { backStack.removeLastOrNull() }) }
    }
    entry<RequestCardsPrototypeRoute>(metadata = DetailPane) {
        DetailPaneContent { RequestCardsPrototypeScreen(onBack = { backStack.removeLastOrNull() }) }
    }
}
