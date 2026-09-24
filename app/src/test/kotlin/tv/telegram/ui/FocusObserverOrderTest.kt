package tv.telegram.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the modifier order the focus visuals depend on.
 *
 * A focus observer only sees the modifiers that follow it, so an `onFocusChanged` /
 * `onFocusState` placed after `.focusable()` sees nothing and the highlight never
 * moves. That is exactly the bug fixed on 2026-09-23, and a composition test cannot
 * catch it on every screen — so this scans the sources instead.
 */
class FocusObserverOrderTest {

    @Test
    fun `no focus observer sits after the focusable it watches`() {
        val roots = listOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
            .filter { it.isDirectory }
        val offenders = mutableListOf<String>()

        roots.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val isObserver = line.contains("onFocusChanged") || line.contains("onFocusState")
                    if (!isObserver) return@forEachIndexed

                    // A correctly placed observer sits just before the focusable it watches.
                    if (lines.drop(index + 1).take(3).any { it.contains(".focusable()") }) return@forEachIndexed

                    // Otherwise, if a focusable precedes it in the same chain, it can never fire.
                    var i = index - 1
                    var guard = 0
                    while (i >= 0 && guard++ < 40) {
                        val prev = lines[i].trim()
                        if (prev.startsWith("private fun ") || prev.startsWith("fun ") ||
                            prev.startsWith("@Composable") || prev.startsWith("internal fun ")
                        ) break
                        if (prev.contains("onFocusChanged") || prev.contains("onFocusState")) break
                        if (prev.contains(".focusable()")) {
                            offenders += "${file.path}: line ${index + 1} observes after focusable at ${i + 1}"
                            break
                        }
                        i--
                    }
                }
            }
        }

        assertTrue(
            "Focus observers must precede .focusable():\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
