package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The addresses typed to send to a TV, kept per server, most recent first. */
class HandOffAddressMemoryTest {
    private val memory = DataStoreHandOffAddressMemory(InMemoryDataStore(), limit = 2)

    @Test
    fun `nothing is remembered for a server never sent from`() =
        runTest {
            assertEquals(emptyList<String>(), memory.remembered("http://192.168.1.10:5055/"))
        }

    @Test
    fun `the latest comes first, a repeat moves up rather than doubling, and the oldest falls off`() =
        runTest {
            val server = "http://192.168.1.10:5055/"
            memory.remember(server, "http://a/")
            memory.remember(server, "http://b/")
            memory.remember(server, "http://a/")
            assertEquals(listOf("http://a/", "http://b/"), memory.remembered(server))

            memory.remember(server, "http://c/")
            assertEquals(listOf("http://c/", "http://a/"), memory.remembered(server))
        }

    @Test
    fun `each server keeps its own, under any spelling of its address`() =
        runTest {
            memory.remember("http://192.168.1.10:5055/", "http://a/")
            memory.remember("https://seerr.example.com/", "http://b/")

            assertEquals(listOf("http://a/"), memory.remembered("HTTP://192.168.1.10:5055"))
            assertEquals(listOf("http://b/"), memory.remembered("https://seerr.example.com"))
        }
}
