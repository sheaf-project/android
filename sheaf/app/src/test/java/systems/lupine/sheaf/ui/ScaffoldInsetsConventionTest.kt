package systems.lupine.sheaf.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The app root holds content clear of the system bars for the whole app, so
 * every screen's own Scaffold has to pass `contentWindowInsets = WindowInsets(0)`
 * or it pads a second time on top of the root's.
 *
 * This is a convention, not something the type system can enforce, and it is
 * invisible until someone looks at a device: a screen that forgets it just
 * carries a bit of extra space under the status bar. This test is the
 * enforcement.
 *
 * It does NOT catch the other half of the same contract, the root itself
 * failing to apply the inset (which is how this regressed in 1.3.0). That is
 * Compose layout behaviour and needs a screenshot or instrumentation test; the
 * project has no harness for either yet.
 */
class ScaffoldInsetsConventionTest {

    private val uiSources: List<File> by lazy {
        // Unit tests run with the module directory as the working directory.
        // Try that, then the repo root, so this works from either.
        val candidates = listOf(
            File("src/main/java/systems/lupine/sheaf/ui"),
            File("sheaf/app/src/main/java/systems/lupine/sheaf/ui"),
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: fail(
                "Could not locate the ui source directory from ${File(".").absolutePath}. " +
                    "Fix the path rather than deleting the test: a convention check that " +
                    "silently scans nothing is worse than no check."
            )
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun `the scan finds the screens it is meant to be checking`() {
        // Guards against the test passing because it looked at an empty list.
        assertTrue(
            uiSources.size > 30,
            "Only found ${uiSources.size} ui source files; the scan path is probably wrong.",
        )
    }

    @Test fun `every screen Scaffold opts out of window insets`() {
        // Matches a call to Scaffold(, not NavigationSuiteScaffold( or
        // CategoryScaffold( (preceded by a word character), and not the line
        // that declares one.
        val callSite = Regex("""(^|[^\w.])Scaffold\s*\(""")
        val offenders = mutableListOf<String>()

        uiSources.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (!callSite.containsMatchIn(line)) return@forEachIndexed
                if ("fun " in line || line.trimStart().startsWith("import ")) return@forEachIndexed
                // The parameter, if present, is within the first handful of
                // arguments; a generous window keeps this robust to formatting.
                val window = lines.subList(index, minOf(index + 30, lines.size))
                if (window.none { "contentWindowInsets" in it }) {
                    offenders += "${file.name}:${index + 1}"
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "These Scaffolds don't pass contentWindowInsets = WindowInsets(0), so they " +
                "will pad below the status bar twice (the app root already insets " +
                "content for every screen):\n" + offenders.joinToString("\n") { "  $it" },
        )
    }
}
