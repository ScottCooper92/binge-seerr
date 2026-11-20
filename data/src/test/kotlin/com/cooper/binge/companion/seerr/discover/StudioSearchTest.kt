package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The company search is asked of a server only if it has it, so an older server never sees a call it would answer with a 404 (#2334). */
class StudioSearchTest {
    private fun search(
        variant: SeerrVariant,
        minor: Int,
        calls: MutableList<String>,
    ) = SeerrStudioSearch(
        api = {
            calls += "api"
            error("the server has no company search")
        },
        profile = { SeerrServerProfile(variant = variant, version = SeerrVersion(1, minor, 0)) },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun `an Overseerr before 1_32 is not asked and answers an empty page`() =
        runTest {
            val calls = mutableListOf<String>()
            val studios = search(SeerrVariant.Overseerr, minor = 31, calls)

            assertFalse(studios.supported())
            assertEquals(emptyList<StudioResult>(), studios.search("lucas", 1).items)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `an Overseerr from 1_32 has the company search`() =
        runTest {
            assertTrue(search(SeerrVariant.Overseerr, minor = 32, mutableListOf()).supported())
        }
}
