package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.notifications.NotificationSignal
import io.github.scottcooper92.binge.seerr.notifications.deepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test
    fun `every link resolves to the hub, the list and the page, so Back walks the way it would by hand`() {
        assertEquals(listOf(HomeRoute, RequestsRoute), DeepLinks.backStackFor(DeepLinks.requests()))
        assertEquals(listOf(HomeRoute, RequestsRoute, RequestDetailRoute(7)), DeepLinks.backStackFor(DeepLinks.request(7)))
        assertEquals(listOf(HomeRoute, IssuesRoute), DeepLinks.backStackFor(DeepLinks.issues()))
        assertEquals(listOf(HomeRoute, IssuesRoute, IssueDetailRoute(3)), DeepLinks.backStackFor(DeepLinks.issue(3)))
        assertEquals(listOf(HomeRoute, UsersRoute, UserDetailRoute(9)), DeepLinks.backStackFor(DeepLinks.user(9)))
        assertEquals(listOf(HomeRoute, SettingsRoute), DeepLinks.backStackFor(DeepLinks.settings()))
        assertEquals(listOf(HomeRoute, SettingsRoute, EditConnectionRoute), DeepLinks.backStackFor(DeepLinks.reconnect()))
    }

    @Test
    fun `anything that is not one of ours is left alone`() {
        assertNull(DeepLinks.backStackFor(null))
        assertNull(DeepLinks.backStackFor("https://seerr.example.com/requests"))
        assertNull(DeepLinks.backStackFor("seerr-companion://request/abc"))
        assertNull(DeepLinks.backStackFor("seerr-companion://nowhere"))
        assertNull(DeepLinks.backStackFor("seerr-companion://requests/extra"))
    }

    @Test
    fun `a batch of one opens its page and a larger one the list it belongs to`() {
        assertEquals(DeepLinks.request(4), NotificationSignal.PendingRequests.deepLink(listOf(4)))
        assertEquals(DeepLinks.requests(), NotificationSignal.PendingRequests.deepLink(listOf(4, 5)))
        assertEquals(DeepLinks.issue(2), NotificationSignal.OpenIssues.deepLink(listOf(2)))
        assertEquals(DeepLinks.issues(), NotificationSignal.OpenIssues.deepLink(listOf(2, 3)))
        assertEquals(DeepLinks.request(8), NotificationSignal.RequestApproved.deepLink(listOf(8)))
        assertEquals(DeepLinks.requests(), NotificationSignal.RequestDeclined.deepLink(listOf(8, 9)))
    }
}
