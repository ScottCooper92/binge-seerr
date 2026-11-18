package com.cooper.binge.companion.seerr.ui.settings.licences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.homelab.core.presentation.appsettings.LicencesViewModel
import com.cooper.homelab.core.uiandroid.appsettings.LicencesScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Holds the shared licences view model for the page, so it is made once and survives a rotation. */
@HiltViewModel
class LicencesHost
    @Inject
    constructor(
        source: LicenceSource,
    ) : ViewModel() {
        val model = LicencesViewModel(viewModelScope, source)
    }

/** The open-source licences page: every library the app ships, with its version and licence, linking to its site. */
@Composable
internal fun LicencesEntry(
    onBack: () -> Unit,
    host: LicencesHost = hiltViewModel(),
) {
    val state by host.model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LicencesScreen(state = state, onBack = onBack, onOpenWebsite = context::openInBrowser)
}
