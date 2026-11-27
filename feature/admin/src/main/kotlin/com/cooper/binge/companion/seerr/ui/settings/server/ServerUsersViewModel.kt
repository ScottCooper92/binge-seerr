package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.ui.users.settings.SaveAsMadeEditorWithExtrasViewModel
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Users settings page, over the same `settings/main` record as General; a write sends only this page's fields. */
@HiltViewModel
class ServerUsersViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
        private val analytics: Analytics = NoOpAnalytics,
    ) : SaveAsMadeEditorWithExtrasViewModel<ServerUsersSettings, ServerUsersExtras>(ServerUsersExtras(), dispatcher, appScope) {
        init {
            reload()
        }

        override suspend fun load(): ServerUsersSettings =
            coroutineScope {
                val profile = async { connection.profile() }
                val main = connection.api().mainSettings()
                val mediaServer = profile.await().mediaServer
                editExtras {
                    it.copy(
                        mediaServer = mediaServer,
                        defaultPermissions =
                            ManageablePermission.decode(
                                main.defaultPermissions ?: 0,
                            ),
                    )
                }
                main.toServerUsers()
            }

        override suspend fun write(draft: ServerUsersSettings): ServerUsersSettings =
            connection
                .api()
                .updateMainSettings(draft.toBody())
                .toServerUsers()
                .also { analytics.serverSettingChanged("server_users") }

        override fun canSave(draft: ServerUsersSettings): Boolean = draft.valid

        /** Re-reads the default permissions on return from their editor, leaving the form's unsaved edits alone. */
        fun refreshDefaultPermissions() {
            viewModelScope.launch(dispatcher) {
                attempt { connection.api().mainSettings() }.onSuccess { main ->
                    editExtras { it.copy(defaultPermissions = ManageablePermission.decode(main.defaultPermissions ?: 0)) }
                }
            }
        }
    }
