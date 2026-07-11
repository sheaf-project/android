package systems.lupine.sheaf.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageSourceClassificationTest {

    @Test fun `relative v1 files path is hosted`() {
        assertTrue(isHostedImageUrl("/v1/files/bios/abc123.webp"))
    }

    @Test fun `signed v1 files path with query is hosted`() {
        assertTrue(isHostedImageUrl("/v1/files/bios/abc123.webp?token=x&expires=1"))
    }

    @Test fun `absolute url through the instance api is hosted`() {
        assertTrue(isHostedImageUrl("https://app.example.com/v1/files/bios/abc.webp"))
    }

    @Test fun `arbitrary external url is external without a cdn base`() {
        assertFalse(isHostedImageUrl("https://imgur.com/cat.png"))
    }

    @Test fun `cdn-served image is hosted when it matches the cdn base`() {
        // The default on the hosted instance: files resolve to the CDN, whose
        // URLs never contain /v1/files/. Before this was recognised, they were
        // misclassified as external.
        assertTrue(isHostedImageUrl("https://cdn.example.com/bios/abc.webp", "https://cdn.example.com"))
    }

    @Test fun `cdn base with trailing slash still matches`() {
        assertTrue(isHostedImageUrl("https://cdn.example.com/bios/abc.webp", "https://cdn.example.com/"))
    }

    @Test fun `url on a different host is external even with a cdn base set`() {
        assertFalse(isHostedImageUrl("https://evil.example.net/bios/abc.webp", "https://cdn.example.com"))
    }

    @Test fun `a cdn-base prefix that is not a path boundary is not hosted`() {
        // Guards against "https://cdn.example.com.evil.net/..." matching the
        // base by bare startsWith; the trailing slash makes it a real boundary.
        assertFalse(isHostedImageUrl("https://cdn.example.com.evil.net/x.png", "https://cdn.example.com"))
    }

    @Test fun `extract classifies each reference and honours the cdn base`() {
        val md = """
            ![a](/v1/files/bios/one.webp)
            ![b](https://cdn.example.com/bios/two.webp)
            ![c](https://imgur.com/three.png)
        """.trimIndent()
        val refs = extractImageReferences(md, cdnBase = "https://cdn.example.com")
        assertEquals(3, refs.size)
        assertTrue(refs[0].hosted)
        assertTrue(refs[1].hosted)
        assertFalse(refs[2].hosted)
    }
}
