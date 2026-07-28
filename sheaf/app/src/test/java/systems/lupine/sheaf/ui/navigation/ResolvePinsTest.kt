package systems.lupine.sheaf.ui.navigation

import systems.lupine.sheaf.ui.Routes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins are persisted user data read back by later builds, so resolution has to
 * cope with routes that no longer exist, duplicates, and over-long lists
 * without producing a dead or ragged bar.
 */
class ResolvePinsTest {

    private fun routesOf(saved: List<String>?) = resolvePins(saved).map { it.route }

    @Test fun `never set seeds the defaults`() {
        assertEquals(DEFAULT_PINS, routesOf(null))
    }

    @Test fun `empty list is a real choice and pins nothing`() {
        // Distinct from null: the user unpinned everything, so the bar is just
        // Home and More rather than silently reverting to the defaults.
        assertEquals(emptyList(), routesOf(emptyList()))
    }

    @Test fun `saved pins are honoured in order`() {
        val saved = listOf(Routes.POLLS, Routes.ANALYTICS, Routes.MESSAGES)
        assertEquals(saved, routesOf(saved))
    }

    @Test fun `unknown routes are dropped rather than rendered as dead slots`() {
        val saved = listOf(Routes.POLLS, "settings/relationships", Routes.MESSAGES)
        assertEquals(listOf(Routes.POLLS, Routes.MESSAGES), routesOf(saved))
    }

    @Test fun `a fully stale list falls back to nothing pinned, not to junk`() {
        assertEquals(emptyList(), routesOf(listOf("nope", "also/nope")))
    }

    @Test fun `duplicates collapse to a single slot`() {
        val saved = listOf(Routes.POLLS, Routes.POLLS, Routes.MESSAGES)
        assertEquals(listOf(Routes.POLLS, Routes.MESSAGES), routesOf(saved))
    }

    @Test fun `over-long lists are capped at the slot count`() {
        val saved = listOf(Routes.POLLS, Routes.ANALYTICS, Routes.MESSAGES, Routes.FILES)
        assertEquals(PIN_SLOTS, routesOf(saved).size)
        assertTrue(Routes.FILES !in routesOf(saved))
    }

    @Test fun `short lists stay short instead of being topped up`() {
        // Padding would mean unpinning something in the editor silently swapped
        // in a default, so the bar and the editor would disagree.
        assertEquals(listOf(Routes.POLLS), routesOf(listOf(Routes.POLLS)))
    }

    @Test fun `home can never be pinned into a second slot`() {
        assertTrue(Routes.HOME !in routesOf(listOf(Routes.HOME, Routes.POLLS)))
        assertTrue(pinnableDests.none { it.route == Routes.HOME })
    }

    @Test fun `defaults are themselves pinnable routes`() {
        // Guards against a rename landing in Routes but not in DEFAULT_PINS,
        // which would silently give new installs an empty bar.
        val pinnable = pinnableDests.map { it.route }.toSet()
        DEFAULT_PINS.forEach { assertTrue(it in pinnable, "default pin $it is not pinnable") }
        assertEquals(PIN_SLOTS, DEFAULT_PINS.size)
    }

    @Test fun `moving an item up swaps it with its predecessor`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(1, 0))
    }

    @Test fun `moving an item down swaps it with its successor`() {
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").moved(1, 2))
    }

    @Test fun `moving past either end is a no-op rather than a crash`() {
        // The end-of-list buttons are disabled, but the helper is what makes
        // that safe rather than merely tidy.
        val list = listOf("a", "b", "c")
        assertEquals(list, list.moved(0, -1))
        assertEquals(list, list.moved(2, 3))
        assertEquals(list, list.moved(1, 1))
    }

    @Test fun `moving preserves every item`() {
        val list = listOf("a", "b", "c")
        assertEquals(list.toSet(), list.moved(0, 2).toSet())
        assertEquals(list.size, list.moved(0, 2).size)
    }

    @Test fun `every drawer destination resolves to a registered route`() {
        // The drawer is the complete list; a typo'd route here would be a row
        // that navigates nowhere.
        val known = Routes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { it.isAccessible = true; it.get(Routes) as? String }
            .toSet()
        allDests.forEach { assertTrue(it.route in known, "drawer route ${it.route} is not in Routes") }
    }
}
