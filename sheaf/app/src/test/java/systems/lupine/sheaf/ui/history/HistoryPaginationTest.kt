package systems.lupine.sheaf.ui.history

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ceil-division page count, which feeds goToPage's coerceIn(1, totalPages).
 * Off by one and the last page is unreachable, or there is a phantom empty one.
 */
class HistoryPaginationTest {

    private fun pages(total: Int?, size: Int) =
        HistoryUiState(totalCount = total, pageSize = size).totalPages

    @Test fun `an exact multiple does not gain an empty page`() {
        assertEquals(2, pages(total = 100, size = 50))
    }

    @Test fun `a partial page counts`() {
        assertEquals(3, pages(total = 101, size = 50))
        assertEquals(1, pages(total = 1, size = 50))
    }

    @Test fun `no rows still means one page`() {
        assertEquals(1, pages(total = 0, size = 50))
    }

    @Test fun `an unknown total means one page`() {
        // Infinite mode never asks for a total; the paged UI must not render a
        // zero-page control while the first response is in flight.
        assertEquals(1, pages(total = null, size = 50))
    }

    @Test fun `a nonsense page size cannot divide by zero`() {
        assertEquals(1, pages(total = 100, size = 0))
        assertEquals(1, pages(total = 100, size = -10))
    }

    @Test fun `each supported page size divides cleanly`() {
        PAGE_SIZE_OPTIONS.forEach { size ->
            assertEquals(1, pages(total = size, size = size), "one full page at size $size")
            assertEquals(2, pages(total = size + 1, size = size), "one over at size $size")
        }
    }
}
