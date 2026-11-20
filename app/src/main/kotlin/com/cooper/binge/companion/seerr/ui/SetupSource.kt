package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cooper.binge.companion.seerr.flags.SharedSetupEnabled
import com.cooper.binge.companion.seerr.ui.tv.tvActions
import com.cooper.homelab.core.uiandroid.flags.LocalFlags
import kotlinx.coroutines.flow.StateFlow

/**
 * What a setup entry needs of its view model, so the entries serve either path: [SetupViewModel], the app's own, or
 * [SharedSetupViewModel], on the shared connect machine. The screens see only [SetupUiState] and [SetupActions] either way.
 */
internal sealed interface SetupSource {
    val uiState: StateFlow<SetupUiState>

    /** Settings' Edit connection, or the sign-in again after the server rejected the session when [notice] says so. */
    fun beginEdit(notice: SetupNotice? = null)
}

/**
 * The path setup runs on, chosen once when the entry first shows, by the shared-setup flag and saved with the entry: a value
 * that arrives mid-setup, or a rotation after it, does not swap the view model under the user.
 */
@Composable
internal fun rememberSetupSource(): SetupSource {
    val enabled = LocalFlags.current[SharedSetupEnabled]
    val shared = rememberSaveable { enabled }
    return if (shared) hiltViewModel<SharedSetupViewModel>() else hiltViewModel<SetupViewModel>()
}

/** The actions for the phone, or for the television when [tv] is set. */
internal fun SetupSource.setupActions(
    tv: Boolean,
    onDisconnect: (() -> Unit)? = null,
): SetupActions =
    when (this) {
        is SetupViewModel -> if (tv) tvActions(onDisconnect) else actions(onDisconnect)
        is SharedSetupViewModel -> actions(tv, onDisconnect)
    }
