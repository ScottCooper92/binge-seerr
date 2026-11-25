package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.title.TitleRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Services sheet reads at most two request pages, however many requests the title has. */
class ServicesRequestIdsTest {
    private fun request(
        id: Int,
        is4k: Boolean = false,
    ) = TitleRequest(id, is4k, status = null, requesterId = null)

    @Test
    fun `a request per season reads only the newest`() {
        assertEquals(listOf(9), servicesRequestIds((1..9).map { request(it) }))
    }

    @Test
    fun `the newest of each quality is read, newest first`() {
        assertEquals(listOf(7, 3), servicesRequestIds(listOf(request(2), request(3, true), request(7), request(4))))
    }

    @Test
    fun `no requests read nothing`() {
        assertEquals(emptyList<Int>(), servicesRequestIds(emptyList()))
    }
}
