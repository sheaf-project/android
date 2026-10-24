package systems.lupine.sheaf.ui.settings

import systems.lupine.sheaf.data.model.FileCleanupPreview
import systems.lupine.sheaf.data.model.FileRead
import systems.lupine.sheaf.di.NetworkModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which files "Delete unused files" offers up.
 *
 * This used to be decided here, from the system avatar and member avatars and
 * nothing else, so a banner, an image embedded in a bio or a journal entry, and
 * anything revision history still held all came back as unused. The button then
 * deleted them, and the server does no reference check of its own on a delete.
 *
 * The answer now comes from the server, which checks all of those places. These
 * tests cover the matching, which is the part still done here, and the two
 * response shapes the preview comes back as.
 */
class OrphanFilesTest {

    private fun file(id: String, key: String, url: String = "/v1/files/$key") = FileRead(
        id = id,
        key = key,
        url = url,
        purpose = "avatar",
        contentType = "image/png",
        sizeBytes = 1,
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Test fun `only the files the server named come back`() {
        val files = listOf(
            file("1", "avatars/sys/a.png"),
            file("2", "banners/sys/b.png"),
            file("3", "bios/sys/c.png"),
        )
        val orphans = orphanFiles(files, listOf("bios/sys/c.png"))
        assertEquals(listOf("3"), orphans.map { it.id })
    }

    @Test fun `a banner the system is using is not offered for deletion`() {
        // The case found in the field: the account's only two files were a
        // member avatar and the live system banner, and the old rule flagged
        // the banner. The server reports no orphans at all here.
        val files = listOf(file("1", "avatars/sys/a.png"), file("2", "banners/sys/b.png"))
        assertTrue(orphanFiles(files, emptyList()).isEmpty())
    }

    @Test fun `matching is on the key, not the serve URL`() {
        // The old rule compared serve URLs. How a file is served depends on the
        // instance: a CDN or a signed URL means the listing's url does not look
        // like anything the server reported, so url matching would come back
        // with either nothing or everything.
        val files = listOf(
            file("1", "avatars/sys/a.png", url = "https://cdn.example.net/avatars/sys/a.png?token=x"),
        )
        assertEquals(listOf("1"), orphanFiles(files, listOf("avatars/sys/a.png")).map { it.id })
    }

    @Test fun `a key the listing does not have is ignored`() {
        // The two calls are not atomic, so the preview can name a file that has
        // since gone. Nothing to delete rather than an invented row.
        assertTrue(orphanFiles(listOf(file("1", "a.png")), listOf("gone.png")).isEmpty())
    }

    @Test fun `the no-orphans response has no keys at all`() {
        // The server omits `keys` entirely when nothing is orphaned, so this
        // has to decode to an empty list rather than failing or reading null.
        val preview = NetworkModule.provideMoshi()
            .adapter(FileCleanupPreview::class.java)
            .fromJson("""{"orphaned":0,"freed_bytes":0,"dry_run":true}""")!!
        assertEquals(0, preview.orphaned)
        assertTrue(preview.keys.isEmpty())
    }

    @Test fun `a preview with orphans carries their keys`() {
        val preview = NetworkModule.provideMoshi()
            .adapter(FileCleanupPreview::class.java)
            .fromJson(
                """{"orphaned":2,"freed_bytes":2048,"dry_run":true,""" +
                    """"keys":["bios/sys/c.png","journals/sys/d.png"]}""",
            )!!
        assertEquals(2, preview.orphaned)
        assertEquals(2048L, preview.freedBytes)
        assertEquals(listOf("bios/sys/c.png", "journals/sys/d.png"), preview.keys)
    }
}
