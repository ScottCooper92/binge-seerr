package io.github.scottcooper92.binge.seerr.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's own links, `seerr-companion://<path>`, carried by a notification and resolved onto
 * the back stack: the hub, then the list, then the page, so Back walks the way it would by hand.
 * One place builds and reads them, so the two cannot drift.
 */
object DeepLinks {
    const val SCHEME = "seerr-companion"
    private const val REQUESTS = "requests"
    private const val REQUEST = "request"
    private const val ISSUES = "issues"
    private const val ISSUE = "issue"
    private const val USER = "user"
    private const val SETTINGS = "settings"
    private const val RECONNECT = "reconnect"

    fun requests(): String = link(REQUESTS)

    fun request(id: Int): String = link(REQUEST, id.toString())

    fun issues(): String = link(ISSUES)

    fun issue(id: Int): String = link(ISSUE, id.toString())

    fun user(id: Int): String = link(USER, id.toString())

    fun settings(): String = link(SETTINGS)

    /** Straight to the sign-in form on the saved server, for the notice that the server rejected the sign-in. */
    fun reconnect(): String = link(RECONNECT)

    /** The back stack [link] opens, hub first; null for anything that is not one of ours. */
    fun backStackFor(link: String?): List<SeerrRoute>? {
        val path = link?.removePrefix("$SCHEME://")?.takeIf { it != link } ?: return null
        val segments = path.trimEnd('/').split('/')
        val id = segments.getOrNull(1)?.toIntOrNull()
        return when {
            segments.size == 1 && segments[0] == REQUESTS -> listOf(HomeRoute, RequestsRoute)
            segments.size == 2 && segments[0] == REQUEST && id != null -> listOf(HomeRoute, RequestsRoute, RequestDetailRoute(id))
            segments.size == 1 && segments[0] == ISSUES -> listOf(HomeRoute, IssuesRoute)
            segments.size == 2 && segments[0] == ISSUE && id != null -> listOf(HomeRoute, IssuesRoute, IssueDetailRoute(id))
            segments.size == 2 && segments[0] == USER && id != null -> listOf(HomeRoute, UsersRoute, UserDetailRoute(id))
            segments.size == 1 && segments[0] == SETTINGS -> listOf(HomeRoute, SettingsRoute)
            segments.size == 1 && segments[0] == RECONNECT -> listOf(HomeRoute, SettingsRoute, EditConnectionRoute)
            else -> null
        }
    }

    private fun link(vararg segments: String): String = "$SCHEME://" + segments.joinToString("/")
}

/**
 * Hands a resolved link from the Activity to the host. Buffered, so a tap that starts the app is
 * delivered once the host is composed and subscribes.
 */
@Singleton
class DeepLinkNavigator
    @Inject
    constructor() {
        private val channel = Channel<List<SeerrRoute>>(capacity = Channel.BUFFERED)

        /** Each emission replaces the back stack, root first. */
        val backStacks: Flow<List<SeerrRoute>> = channel.receiveAsFlow()

        fun open(link: String?) {
            DeepLinks.backStackFor(link)?.let { channel.trySend(it) }
        }
    }
