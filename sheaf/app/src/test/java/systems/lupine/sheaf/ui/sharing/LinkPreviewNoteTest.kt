package systems.lupine.sheaf.ui.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import systems.lupine.sheaf.data.model.PREVIEW_GENERIC
import systems.lupine.sheaf.data.model.PREVIEW_SYSTEM_DETAILS

class LinkPreviewNoteTest {

    private fun note(
        card: PreviewCard = PreviewCard.PROFILE,
        live: String = PREVIEW_GENERIC,
        pending: String? = null,
        effective: String = PREVIEW_GENERIC,
        permalinks: Boolean = true,
    ) = previewNote(card, live, pending, effective, permalinks)

    @Test
    fun `nothing to explain when the card is off`() {
        assertNull(note())
    }

    @Test
    fun `nothing to explain when the rich card is really being served`() {
        assertNull(note(live = PREVIEW_SYSTEM_DETAILS, effective = PREVIEW_SYSTEM_DETAILS))
    }

    // The case that went unexplained on web: the live mode stays generic while
    // a raise is staged, so a note keyed on the live mode never appeared.
    @Test
    fun `a staged raise is explained even though the live mode is still generic`() {
        val n = note(pending = PREVIEW_SYSTEM_DETAILS)
        assertTrue(n != null && "grace period" in n, "got: $n")
    }

    @Test
    fun `on but generic points at share links being the reason`() {
        val n = note(live = PREVIEW_SYSTEM_DETAILS)
        assertTrue(n != null && "share link" in n, "got: $n")
    }

    @Test
    fun `a member card with permalinks off says so`() {
        assertEquals(
            "Member permalinks are off, so there are no member links to preview.",
            note(card = PreviewCard.MEMBER, live = PREVIEW_SYSTEM_DETAILS, permalinks = false),
        )
    }

    @Test
    fun `permalinks only matter for the member card`() {
        val n = note(card = PreviewCard.PROFILE, live = PREVIEW_SYSTEM_DETAILS, permalinks = false)
        assertTrue(n != null && "share link" in n, "got: $n")
    }

    @Test
    fun `a staged raise outranks permalinks being off`() {
        val n = note(card = PreviewCard.MEMBER, pending = PREVIEW_SYSTEM_DETAILS, permalinks = false)
        assertTrue(n != null && "grace period" in n, "got: $n")
    }
}
