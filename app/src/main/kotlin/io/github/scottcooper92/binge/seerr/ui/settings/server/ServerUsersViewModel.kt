package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorViewModel
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
    ) : ExtrasEditorViewModel<ServerUsersSettings, ServerUsersExtras>(ServerUsersExtras(), dispatcher) {
        /** Users saves as it changes (#930): each default, limit and switch stands alone, and settings/main merges what it is sent. */
        override val saveAsMadeScope: CoroutineScope = appScope

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
            connection.api().updateMainSettings(draft.toBody()).toServerUsers()

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
