package com.cooper.binge.companion.seerr.ui

import androidx.lifecycle.SavedStateHandle
import com.cooper.binge.companion.seerr.auth.PlexPinExpiredException
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.QuickConnectExpiredException
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrQuickConnect
import com.cooper.homelab.core.presentation.connect.Link
import com.cooper.homelab.core.presentation.connect.LinkExpiredException
import com.cooper.homelab.core.presentation.connect.LinkProvider
import com.cooper.homelab.core.presentation.connect.LinkStash
import com.cooper.homelab.core.presentation.connect.ProbedServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

private const val SHARED_PENDING_LINK = "sharedPendingLink"

/**
 * The pending link of the shared setup, kept in the saved state so it outlives the process dying while the user approves elsewhere.
 * Its own key, so a record an earlier build left under `pendingLink`, of another shape, is never read as this one.
 */
internal class SavedStateLinkStash(
    private val savedState: SavedStateHandle,
) : LinkStash {
    override fun read(): String? = savedState.get<String>(SHARED_PENDING_LINK)

    override fun write(value: String) {
        savedState[SHARED_PENDING_LINK] = value
    }

    override fun clear() {
        savedState.remove<String>(SHARED_PENDING_LINK)
    }
}

/**
 * Plex: a PIN approved on plex.tv, whose token Seerr signs in with. [forTelevision] mints the short PIN typed at plex.tv/link,
 * not the long one a phone opens in a browser. What is kept to pick the wait up again is the PIN's id and when it expires.
 */
internal class SeerrPlexLink(
    private val connection: SeerrConnection,
    private val plex: PlexPinFlow,
) : LinkProvider<SetupServer, Unit> {
    override suspend fun start(
        server: ProbedServer<SetupServer>,
        forTelevision: Boolean,
    ): Link {
        val pin = plex.start(forTelevision)
        return Link(code = pin.code, openUrl = pin.authUrl, resumeWith = keep(pin.id, pin.expiresAt))
    }

    override suspend fun await(
        server: ProbedServer<SetupServer>,
        link: Link,
    ) {
        val (id, expiresAt) = kept(requireNotNull(link.resumeWith))
        val pin = plex.resume(id, link.code, expiresAt)
        try {
            connection.logInWithPlex(server.preview.baseUrl, plex.awaitToken(pin)).getOrThrow()
        } catch (expired: PlexPinExpiredException) {
            throw LinkExpiredException().apply { initCause(expired) }
        }
    }

    override suspend fun resume(
        server: ProbedServer<SetupServer>,
        code: String,
        resumeWith: String,
    ): Link {
        val (id, expiresAt) = kept(resumeWith)
        return Link(code = code, openUrl = plex.resume(id, code, expiresAt).authUrl, resumeWith = resumeWith)
    }

    private fun keep(
        id: Long,
        expiresAt: Instant?,
    ): String =
        JsonObject(
            buildMap {
                put("id", JsonPrimitive(id))
                expiresAt?.let { put("expiresAt", JsonPrimitive(it.toEpochMilli())) }
            },
        ).toString()

    private fun kept(resumeWith: String): Pair<Long, Instant?> {
        val record = Json.parseToJsonElement(resumeWith) as JsonObject
        val id = (record["id"] as JsonPrimitive).longOrNull ?: error("the kept PIN has no id")
        return id to (record["expiresAt"] as? JsonPrimitive)?.longOrNull?.let(Instant::ofEpochMilli)
    }
}

/** Quick Connect on the server's Jellyfin: a code approved there, and a secret that alone finishes the sign-in, kept to resume with. */
internal class SeerrQuickConnectLink(
    private val connection: SeerrConnection,
) : LinkProvider<SetupServer, Unit> {
    override suspend fun start(
        server: ProbedServer<SetupServer>,
        forTelevision: Boolean,
    ): Link {
        val session = connection.startQuickConnect(server.preview.baseUrl).getOrThrow()
        return Link(code = session.code, resumeWith = session.secret)
    }

    override suspend fun await(
        server: ProbedServer<SetupServer>,
        link: Link,
    ) {
        val session = SeerrQuickConnect(code = link.code, secret = requireNotNull(link.resumeWith))
        try {
            connection.finishQuickConnect(server.preview.baseUrl, session).getOrThrow()
        } catch (expired: QuickConnectExpiredException) {
            throw LinkExpiredException().apply { initCause(expired) }
        }
    }

    override suspend fun resume(
        server: ProbedServer<SetupServer>,
        code: String,
        resumeWith: String,
    ): Link = Link(code = code, resumeWith = resumeWith)
}
