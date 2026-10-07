package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The server's TMDB-backed lists a picker offers: countries for discovery, those with streaming providers, languages. */
enum class ServerList { DiscoverRegions, StreamingRegions, Languages }

/** One entry of such a list: its ISO code, and TMDB's English name for when the device has none of its own. */
data class ListEntry(
    val code: String,
    val englishName: String?,
)

/**
 * The server's lists, read the first time a picker needs one and kept for the session. They are TMDB's countries and
 * languages, which barely change, so a second open is instant; the key includes the server, so switching server reads
 * its lists afresh. A failed read is not kept, so the next open tries again.
 */
@Singleton
class ServerListCatalog
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) {
        private val lock = Mutex()
        private val lists = mutableMapOf<Pair<String, ServerList>, List<ListEntry>>()

        /** [kind]'s entries on the saved server; throws when the server can't be read. */
        suspend fun entries(kind: ServerList): List<ListEntry> {
            val key = connection.current().baseUrl to kind
            lock.withLock { lists[key] }?.let { return it }
            val api = connection.api()
            val read =
                when (kind) {
                    ServerList.DiscoverRegions -> api.regions().map { ListEntry(it.code, it.englishName) }
                    ServerList.StreamingRegions -> api.watchProviderRegions().map { ListEntry(it.code, it.englishName) }
                    ServerList.Languages -> api.languages().map { ListEntry(it.code, it.englishName) }
                }.filter { it.code.isNotBlank() }.distinctBy { it.code }
            lock.withLock { lists[key] = read }
            return read
        }
    }

/** One list as the page holds it: being read, read, or failed; absent until a picker asks. */
sealed interface ListChoices {
    data object Loading : ListChoices

    data class Ready(
        val entries: List<ListEntry>,
    ) : ListChoices

    data object Failed : ListChoices
}
