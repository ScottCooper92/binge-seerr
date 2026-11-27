package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.ui.users.settings.SaveAsMadeEditorViewModel
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * The network page: the switches, and on Seerr 3 the proxy and the DNS cache, as one record. It saves as it changes
 * (#930): each write is the saved record with the change in it, and `settings/network` merges what it is sent. A change
 * that leaves the record invalid, a proxy switched on with no address yet, say, is held until it is valid again.
 */
@HiltViewModel
class NetworkViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
        private val analytics: Analytics = NoOpAnalytics,
    ) : SaveAsMadeEditorViewModel<NetworkForm>(dispatcher, appScope) {
        init {
            reload()
        }

        override suspend fun load(): NetworkForm = connection.api().networkSettings().toForm()

        override suspend fun write(draft: NetworkForm): NetworkForm =
            connection
                .api()
                .updateNetworkSettings(draft.toDto())
                .toForm()
                .also { analytics.serverSettingChanged("network") }

        override fun canSave(draft: NetworkForm): Boolean = draft.valid
    }
