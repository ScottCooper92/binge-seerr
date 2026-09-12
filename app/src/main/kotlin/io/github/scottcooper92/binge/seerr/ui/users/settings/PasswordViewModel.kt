package io.github.scottcooper92.binge.seerr.ui.users.settings

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserPasswordBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
        @Assisted private val userId: Int,
    ) : EditorViewModel<PasswordSettings>() {
        init {
            reload()
        }

        override suspend fun load(): PasswordSettings =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val info = api.userPasswordInfo(userId)
                val isSelf = viewer.await().id == userId
                PasswordSettings(hasPassword = info.hasPassword, currentRequired = isSelf && info.hasPassword)
            }

        override suspend fun write(draft: PasswordSettings): PasswordSettings {
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

        override fun canSave(draft: PasswordSettings): Boolean = draft.valid

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): PasswordViewModel
        }
    }
