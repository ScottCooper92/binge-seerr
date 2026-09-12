package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeerrRequestOptionsTest {
    private val hd = SeerrServerDto(id = 1, name = "Radarr")
    private val hdDefault = SeerrServerDto(id = 2, name = "Radarr (default)", isDefault = true)
    private val uhd = SeerrServerDto(id = 3, name = "Radarr 4K", is4k = true)

    @Test
    fun `a request only sees the servers of its own resolution`() {
        val servers = listOf(hd, hdDefault, uhd)

        assertEquals(listOf(hd, hdDefault), servers.forRequest(is4k = false))
        assertEquals(listOf(uhd), servers.forRequest(is4k = true))
    }

    @Test
    fun `the picker opens on the default server, else the first`() {
        assertEquals(hdDefault, listOf(hd, hdDefault).preferred())
        assertEquals(hd, listOf(hd, uhd).preferred())
        assertNull(emptyList<SeerrServerDto>().preferred())
    }
}
