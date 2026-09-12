package io.github.scottcooper92.binge.seerr.ui.users

/** A local account being created. The web client requires both names; the server would take the email alone. */
data class CreateUserDraft(
    val email: String = "",
    val username: String = "",
    val password: String = "",
    /** Ask the server to generate the password and email it; the server allows this only with email set up. */
    val generatePassword: Boolean = false,
    val canGeneratePassword: Boolean = false,
) {
    val valid: Boolean
        get() = email.contains('@') && username.isNotBlank() && (generatePassword || password.length >= MIN_PASSWORD_LENGTH)

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/** One media-server account offered for import: what to show, and the id the import takes. */
data class ImportCandidate(
    val id: String,
    val name: String,
    val email: String?,
    val avatarUrl: String?,
)

/** The import picker: the accounts not yet on the server, and the ones ticked. */
data class ImportPicker(
    val source: UserOrigin,
    val candidates: List<ImportCandidate>? = null,
    val selected: Set<String> = emptySet(),
    val failed: Boolean = false,
)

/** What the add-user flow is showing, if anything. */
sealed interface UserAdmissionState {
    val saving: Boolean

    /** The choice between a local account and an import, where the server offers both. */
    data class Choosing(
        override val saving: Boolean = false,
    ) : UserAdmissionState

    data class Creating(
        val draft: CreateUserDraft,
        override val saving: Boolean = false,
    ) : UserAdmissionState

    data class Importing(
        val picker: ImportPicker,
        override val saving: Boolean = false,
    ) : UserAdmissionState
}
