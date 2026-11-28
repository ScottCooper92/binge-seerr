package com.cooper.binge.companion.seerr.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The signed-in user and their quota, as the account action in a title bar draws them. */
data class AccountBar(
    val account: HubAccount,
    val quota: HubQuota?,
)

/** The account in a title bar: read once per saved connection, and absent where the read fails, so a bar without it is simply a bar. */
sealed interface AccountBarState {
    data object Loading : AccountBarState

    data class Ready(
        val bar: AccountBar,
    ) : AccountBarState

    data object Absent : AccountBarState
}

/**
 * What the account action in the Search and Discover grid bars shows. It reads the user and quota alone, never the dashboard's counts or
 * downloads. Screens above the tab roots share nothing with the Settings dashboard, so each reads it afresh, `Lazily`.
 * The read is keyed on the saved credentials, so Edit connection to another account shows that account at once.
 */
@HiltViewModel
class AccountBarViewModel
    @Inject
    constructor(
        private val loader: HubOverviewLoader,
        connection: SeerrConnection,
    ) : ViewModel() {
        val uiState: StateFlow<AccountBarState> =
            connection.credentials
                .distinctUntilChanged()
                .flatMapLatest { credentials ->
                    flow {
                        emit(AccountBarState.Loading)
                        emit(
                            credentials?.let { loader.accountBar() }?.let(AccountBarState::Ready) ?: AccountBarState.Absent,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, AccountBarState.Loading)
    }
