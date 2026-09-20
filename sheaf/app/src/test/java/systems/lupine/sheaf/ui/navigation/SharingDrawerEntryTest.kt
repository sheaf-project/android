package systems.lupine.sheaf.ui.navigation

import systems.lupine.sheaf.ui.Routes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Sharing entry's reachability rule, which is a safety property rather than
 * a layout one: whoever has something published must always be able to get to
 * the screen that takes it down.
 */
class SharingDrawerEntryTest {

    private fun routesIn(groups: List<DrawerGroup>) =
        groups.flatMap { it.items }.map { it.route }

    @Test fun `hidden when there is no sharing to manage`() {
        assertFalse(Routes.SHARING in routesIn(drawerGroupsFor(showSharing = false)))
    }

    @Test fun `listed with the rest of the system when it is available`() {
        val groups = drawerGroupsFor(showSharing = true)
        assertTrue(Routes.SHARING in routesIn(groups))
        val system = groups.single { it.title == "System" }
        assertEquals(Routes.SHARING, system.items.last().route)
    }

    @Test fun `showing it adds exactly one row`() {
        // A regression here would mean the caller's flag either dropped an
        // existing destination or duplicated one.
        val off = routesIn(drawerGroupsFor(showSharing = false))
        val on = routesIn(drawerGroupsFor(showSharing = true))
        assertEquals(off.size + 1, on.size)
        assertEquals(off, on - Routes.SHARING)
    }

    @Test fun `it keeps the bottom bar when it is open`() {
        // Chrome is decided by membership of this set, so a top-level
        // destination missing from it loses the bar and the drawer swipe.
        assertTrue(Routes.SHARING in drawerRoutes)
    }

    @Test fun `it can be pinned like anything else`() {
        assertTrue(pinnableDests.any { it.route == Routes.SHARING })
        assertEquals(listOf(Routes.SHARING), resolvePins(listOf(Routes.SHARING)).map { it.route })
    }

    @Test fun `a pinned slot survives the instance switch going off`() {
        // The pin is the user's decision. An operator flipping a switch must
        // not silently rearrange somebody's bar, so the slot stays and is drawn
        // dimmed; unpinning it is then their call rather than something that
        // happened to them.
        assertEquals(
            listOf(Routes.SHARING),
            resolvePins(listOf(Routes.SHARING)).map { it.route },
        )
        assertTrue(Routes.SHARING in unavailableRoutes(sharingAvailable = false))
    }

    @Test fun `nothing is dimmed while sharing is available`() {
        assertTrue(unavailableRoutes(sharingAvailable = true).isEmpty())
    }

    @Test fun `the dimmed slot's message says nothing was revoked`() {
        // The one thing somebody must not conclude from a greyed-out Sharing
        // slot is that their published page went away with it.
        val message = unavailableRoutes(sharingAvailable = false).getValue(Routes.SHARING)
        assertTrue("kept" in message && "not revoked" in message, message)
    }
}
