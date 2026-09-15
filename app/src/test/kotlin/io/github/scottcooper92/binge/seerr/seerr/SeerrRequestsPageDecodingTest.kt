package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `GET request` pages, decoded with the same `Json` the API client uses. The bodies are trimmed from
 * a Jellyseerr 2.7.3 response; a single request the page cannot decode fails the whole list.
 */
class SeerrRequestsPageDecodingTest {
    private val json = SeerrApiFactory(logRequests = false).json

    private fun page(tags: String?): String {
        val tagsField = tags?.let { "\"tags\":$it," }.orEmpty()
        return """
            {"pageInfo":{"pages":1,"pageSize":20,"results":1,"page":1},"results":[{"id":33,"status":1,
            "createdAt":"2026-09-12T18:53:32.000Z","updatedAt":"2026-09-12T18:53:32.000Z","type":"movie","is4k":false,
            "serverId":null,"profileId":null,"rootFolder":null,"languageProfileId":null,$tagsField"isAutoRequest":false,
            "media":{"downloadStatus":[],"downloadStatus4k":[],"id":28,"mediaType":"movie","tmdbId":969681,"status":2,"status4k":1},
            "seasons":[],"modifiedBy":null,"requestedBy":{"id":3,"displayName":"Claude"},"seasonCount":0}]}
            """.trimIndent()
    }

    private fun decode(body: String) = json.decodeFromString(SeerrRequestsPageDto.serializer(), body)

    /** What the server sends for a request made without tags: the column is nullable. */
    @Test
    fun `a request whose tags are null decodes, with no tags`() {
        val request = decode(page(tags = "null")).results.single()

        assertEquals(33, request.id)
        assertNull(request.tags)
        assertEquals(emptyList<Int>(), request.tags.orEmpty())
    }

    @Test
    fun `a request without a tags field decodes, with no tags`() {
        assertNull(decode(page(tags = null)).results.single().tags)
    }

    @Test
    fun `a request's tags decode when it has them`() {
        assertEquals(listOf(3, 7), decode(page(tags = "[3,7]")).results.single().tags)
    }
}
