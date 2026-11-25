package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.SeerrUserPasswordBody
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.presentation.editor.CommitEvent
import com.cooper.homelab.core.presentation.editor.CommitForm
import com.cooper.homelab.core.presentation.editor.CommitState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The password page. The server wants the current password only from a user changing their own
 * while they have one; a manager sets another's outright. A save leaves the fields blank: the
 * secret is never held past the write.
 */
@HiltViewModel(assistedFactory = PasswordViewModel.Factory::class)
class PasswordViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @Assisted private val userId: Int,
    ) : ViewModel() {
        private val form =
            CommitForm.plain<PasswordSettings>(
                scope = viewModelScope,
                dispatcher = dispatcher,
                load = ::load,
                write = ::write,
                classify = { it.toServerFailure() },
                canCommit = { it.valid },
            )

        /** What the screen shows: the form's own state. */
        val state: StateFlow<CommitState<PasswordSettings, Unit>> = form.state

        /** What happened once: the change went through, was refused, or failed. */
        val events: Flow<CommitEvent> = form.events

        init {
            form.reload()
        }

        fun reload() = form.reload()

        fun edit(transform: (PasswordSettings) -> PasswordSettings) = form.edit(transform)

        fun save() = form.commit()

        private suspend fun load(): PasswordSettings =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val info = api.userPasswordInfo(userId)
                val isSelf = viewer.await().id == userId
                PasswordSettings(hasPassword = info.hasPassword, currentRequired = isSelf && info.hasPassword)
            }

        private suspend fun write(draft: PasswordSettings): PasswordSettings {
            connection.api().updateUserPassword(
                userId,
                SeerrUserPasswordBody(
                    currentPassword = draft.current.takeIf { draft.currentRequired },
                    newPassword = draft.new,
                    confirmPassword = draft.confirm,
                ),
            )
            val isSelf = connection.authenticatedUser().id == userId
            return PasswordSettings(hasPassword = true, currentRequired = isSelf)
        }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): PasswordViewModel
        }
    }
