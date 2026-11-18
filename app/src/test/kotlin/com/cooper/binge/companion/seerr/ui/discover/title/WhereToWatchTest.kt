package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.discover.title.TitleProvider
import com.cooper.binge.companion.seerr.discover.title.TitleProviders
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun provider(
    id: Int,
    name: String,
) = TitleProvider(id, name, null)

private fun providers(
    streaming: List<TitleProvider>,
    buy: List<TitleProvider>,
    link: String? = "https://watch.example/gb",
) = TitleProviders("GB", link, streaming, buy)

@RunWith(RobolectricTestRunner::class)
class WhereToWatchTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(
        providers: TitleProviders?,
        opened: MutableList<String> = mutableListOf(),
    ) = rule.setContent { SeerrTheme { WhereToWatchSection(providers, onOpenLink = { opened += it }) } }

    @Test
    fun `only the kinds that have providers are offered, in the order the page gives them`() {
        assertEquals(
            listOf(WatchKind.Stream, WatchKind.Buy),
            providers(listOf(provider(1, "A")), listOf(provider(2, "B"))).availableKinds(),
        )
        assertEquals(listOf(WatchKind.Buy), providers(emptyList(), listOf(provider(2, "B"))).availableKinds())
        assertEquals(emptyList<WatchKind>(), providers(emptyList(), emptyList()).availableKinds())
    }

    @Test
    fun `a title with providers of neither kind draws no section`() {
        show(providers(emptyList(), emptyList()))

        rule.onAllNodesWithText("Where to watch").assertCountEquals(0)
        rule.onAllNodesWithText("Data provided by JustWatch").assertCountEquals(0)
    }

    @Test
    fun `a title with no provider data at all draws no section`() {
        show(null)

        rule.onAllNodesWithText("Where to watch").assertCountEquals(0)
    }

    @Test
    fun `the section starts on streaming and the Buy chip shows the other providers`() {
        show(providers(listOf(provider(1, "Stream One")), listOf(provider(2, "Store One"))))

        rule.onNodeWithText("Where to watch").assertIsDisplayed()
        rule.onNodeWithText("Stream One").assertIsDisplayed()
        rule.onAllNodesWithText("Store One").assertCountEquals(0)

        rule.onNodeWithText("Buy").performClick()

        rule.onNodeWithText("Store One").assertIsDisplayed()
        rule.onAllNodesWithText("Stream One").assertCountEquals(0)
        rule.onNodeWithText("Data provided by JustWatch").assertIsDisplayed()
    }

    @Test
    fun `a provider the server lists twice in a kind is shown once`() {
        show(providers(listOf(provider(1, "Stream One"), provider(1, "Stream One")), emptyList()))

        rule.onAllNodesWithText("Stream One").assertCountEquals(1)
    }

    @Test
    fun `a title that can only be bought shows only the Buy chip`() {
        show(providers(emptyList(), listOf(provider(2, "Store One"))))

        rule.onAllNodesWithText("Stream").assertCountEquals(0)
        rule.onNodeWithText("Buy").assertIsDisplayed()
        rule.onNodeWithText("Store One").assertIsDisplayed()
    }

    @Test
    fun `a tap on a provider opens the server's page for the region`() {
        val opened = mutableListOf<String>()
        show(providers(listOf(provider(1, "Stream One")), emptyList()), opened)

        rule.onNodeWithText("Stream One").performClick()

        assertEquals(listOf("https://watch.example/gb"), opened)
    }

    @Test
    fun `with no page for the region a tap on a provider opens nothing`() {
        val opened = mutableListOf<String>()
        show(providers(listOf(provider(1, "Stream One")), emptyList(), link = null), opened)

        rule.onNodeWithText("Stream One").performClick()

        assertEquals(emptyList<String>(), opened)
    }
}
