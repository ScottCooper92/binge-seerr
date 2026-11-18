package com.cooper.binge.companion.seerr.ui.hubs.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubLoaderTest {
    /** A loader whose rows answer when the test says so. */
    private class Rows {
        val gates = HashMap<String, CompletableDeferred<List<Int>>>()
        val calls = mutableListOf<String>()

        suspend fun load(key: String): List<Int> {
            calls += key
            return gates.getOrPut(key) { CompletableDeferred() }.await()
        }

        fun answer(
            key: String,
            items: List<Int>,
        ) = gates.getOrPut(key) { CompletableDeferred() }.complete(items)

        fun fail(
            key: String,
            error: Throwable = IllegalStateException(key),
        ) = gates.getOrPut(key) { CompletableDeferred() }.completeExceptionally(error)

        /** The next load of [key] waits for a new answer. */
        fun reset(key: String) = gates.remove(key)
    }

    private fun TestScope.loader(rows: Rows) = HubLoader<String, Int>(backgroundScope, UnconfinedTestDispatcher(testScheduler), rows::load)

    private fun HubLoader<String, Int>.ready() = state.value as HubState.Ready<String, Int>

    @Test
    fun `nothing has resolved so the hub is loading`() =
        runTest {
            val loader = loader(Rows())
            loader.show(listOf("a", "b"))

            assertSame(HubState.Loading, loader.state.value)
        }

    @Test
    fun `no rows is an empty hub`() =
        runTest {
            val loader = loader(Rows())
            loader.show(emptyList())

            assertSame(HubState.Empty, loader.state.value)
        }

    @Test
    fun `a row shows as soon as it lands and the others fill in below`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b", "c"))

            rows.answer("b", listOf(2))
            assertEquals(listOf("b"), loader.ready().rows.map { it.key })
            assertEquals(2, loader.ready().pending)

            rows.answer("a", listOf(1))
            rows.answer("c", listOf(3))
            assertEquals("the order asked for, whatever order they landed in", listOf("a", "b", "c"), loader.ready().rows.map { it.key })
            assertEquals(0, loader.ready().pending)
        }

    @Test
    fun `a row that fails is left out and never blocks the rest`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b", "c"))

            rows.fail("a")
            rows.answer("b", listOf(2))
            rows.answer("c", listOf(3))

            assertEquals(listOf("b", "c"), loader.ready().rows.map { it.key })
            assertEquals(0, loader.ready().pending)
        }

    @Test
    fun `a row that comes back empty is left out`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b"))

            rows.answer("a", emptyList())
            rows.answer("b", listOf(2))

            assertEquals(listOf("b"), loader.ready().rows.map { it.key })
        }

    @Test
    fun `when every row fails the hub fails with the first error`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b"))
            val first = IllegalStateException("first")

            rows.fail("a", first)
            rows.fail("b")

            assertEquals(first.message, (loader.state.value as HubState.Failed).error.message)
        }

    @Test
    fun `rows that answer empty or fail with none loaded is an empty hub unless every one failed`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b"))

            rows.fail("a")
            rows.answer("b", emptyList())

            assertSame(HubState.Empty, loader.state.value)
        }

    @Test
    fun `a reorder keeps what is loaded and loads nothing again`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b"))
            rows.answer("a", listOf(1))
            rows.answer("b", listOf(2))

            loader.show(listOf("b", "a"))

            assertEquals(listOf("b", "a"), loader.ready().rows.map { it.key })
            assertEquals(listOf("a", "b"), rows.calls)
        }

    @Test
    fun `a row switched on later loads and the rest are not asked again`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a"))
            rows.answer("a", listOf(1))

            loader.show(listOf("a", "b"))
            assertEquals(1, loader.ready().pending)
            rows.answer("b", listOf(2))

            assertEquals(listOf("a", "b"), loader.ready().rows.map { it.key })
            assertEquals(listOf("a", "b"), rows.calls)
        }

    @Test
    fun `a row switched off is dropped and a late answer for it is ignored`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b"))
            rows.answer("a", listOf(1))

            loader.show(listOf("a"))
            rows.answer("b", listOf(2))

            assertEquals(listOf("a"), loader.ready().rows.map { it.key })
        }

    @Test
    fun `a refresh keeps the old items on screen until the new ones land`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a"))
            rows.answer("a", listOf(1))
            rows.reset("a")

            loader.refresh()

            assertTrue(loader.refreshing.value)
            assertEquals(
                listOf(1),
                loader
                    .ready()
                    .rows
                    .single()
                    .items,
            )
            rows.answer("a", listOf(1, 2))
            assertEquals(
                listOf(1, 2),
                loader
                    .ready()
                    .rows
                    .single()
                    .items,
            )
            assertFalse(loader.refreshing.value)
        }

    @Test
    fun `a refresh that fails keeps what the row showed`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a"))
            rows.answer("a", listOf(1))
            rows.reset("a")

            loader.refresh()
            rows.fail("a")

            assertEquals(
                listOf(1),
                loader
                    .ready()
                    .rows
                    .single()
                    .items,
            )
            assertFalse(loader.refreshing.value)
        }

    @Test
    fun `a failed row is tried again by a refresh`() =
        runTest {
            val rows = Rows()
            val loader = loader(rows)
            loader.show(listOf("a", "b"))
            rows.fail("a")
            rows.answer("b", listOf(2))
            rows.reset("a")

            loader.refresh()
            rows.answer("a", listOf(1))

            assertEquals(listOf("a", "b"), loader.ready().rows.map { it.key })
        }
}
