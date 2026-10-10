package io.github.scottcooper92.binge.seerr.service

import android.content.Context
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.UserFacingRefusal

/** A sentence for the user, and the BCP 47 locale it is written in. */
data class UserSentence(
    val message: String,
    val locale: String,
)

/**
 * Where the exported Service finds the sentence for a [UserFacingRefusal]. The contracts say it is in the device's
 * locale (binge-companions#177): host and integration share a device, and only the handshake carries
 * `HostInfo.locale`, which an integration does not keep. So the sentence is resolved as every other string here is,
 * and tagged with the locale it resolved in (`companion_refusal_locale`), which is not the device's when the device speaks a language this app does not ship.
 */
fun interface UserMessages {
    fun sentence(refusal: UserFacingRefusal): UserSentence?

    companion object {
        /** No sentences: the host falls back to its own copy for each code. For a JVM test that does not ask about them. */
        val None: UserMessages = UserMessages { null }
    }
}

/** [UserMessages] from this app's own strings, in whatever locale the device has. */
class AndroidUserMessages(
    private val context: Context,
) : UserMessages {
    override fun sentence(refusal: UserFacingRefusal): UserSentence {
        val res =
            when (refusal) {
                UserFacingRefusal.QuotaSpent -> R.string.companion_refusal_quota
                UserFacingRefusal.Blocklisted -> R.string.companion_refusal_blocklisted
            }
        return UserSentence(
            context.getString(res),
            context.getString(R.string.companion_refusal_locale),
        )
    }
}
