package io.github.scottcooper92.binge.seerr.ui.requests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [RequestDestination.tagsLabel] is the one place tags become display text, for the card and the TV page. */
class RequestDestinationTest {
    private fun destination(vararg tags: String) = RequestDestination(null, null, null, tags.toList())

    @Test
    fun `no tags is no line`() = assertNull(destination().tagsLabel)

    @Test
    fun `one tag is that tag`() = assertEquals("4k", destination("4k").tagsLabel)

    @Test
    fun `several tags are joined with a comma and a space in order`() =
        assertEquals("anime, kids, 4k", destination("anime", "kids", "4k").tagsLabel)
}
