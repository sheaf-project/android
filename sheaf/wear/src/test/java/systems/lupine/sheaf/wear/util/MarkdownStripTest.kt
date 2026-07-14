package systems.lupine.sheaf.wear.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The watch can't render markdown, so it strips it. Getting the regexes wrong
 * either leaks raw `**`/`[]()` syntax into member descriptions or eats content.
 */
class MarkdownStripTest {

    @Test fun `bold and italic are unwrapped`() {
        assertEquals("bold", stripMarkdown("**bold**"))
        assertEquals("bold", stripMarkdown("__bold__"))
        assertEquals("italic", stripMarkdown("*italic*"))
        assertEquals("italic", stripMarkdown("_italic_"))
    }

    @Test fun `bold is stripped before italic so no stray star is left`() {
        assertEquals("both", stripMarkdown("***both***"))
    }

    @Test fun `links keep their text and drop the url`() {
        assertEquals("my site", stripMarkdown("[my site](https://example.org)"))
    }

    @Test fun `images are dropped entirely`() {
        assertEquals("", stripMarkdown("![alt](https://example.org/a.png)").trim())
    }

    @Test fun `inline code is unwrapped`() {
        assertEquals("code", stripMarkdown("`code`"))
    }

    @Test fun `leading block markers are stripped`() {
        assertEquals("Heading", stripMarkdown("# Heading"))
        assertEquals("quote", stripMarkdown("> quote"))
        assertEquals("item", stripMarkdown("- item"))
        assertEquals("item", stripMarkdown("1. item"))
    }

    @Test fun `plain text is untouched`() {
        assertEquals("just a normal sentence", stripMarkdown("just a normal sentence"))
    }

    @Test fun `empty input stays empty`() {
        assertEquals("", stripMarkdown(""))
    }

    @Test fun `a realistic multi-line description flattens cleanly`() {
        val input = "**Name**: Alex\n- likes: [tea](https://tea.example)\n> a note"
        val output = stripMarkdown(input)
        assertEquals("Name: Alex\nlikes: tea\na note", output)
    }
}
