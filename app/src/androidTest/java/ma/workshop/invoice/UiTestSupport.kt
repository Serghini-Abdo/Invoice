package ma.workshop.invoice

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot

/**
 * One line listing the texts, content descriptions and test tags on screen, so a failed wait says what
 * the screen actually showed (CI has no screenshot of a failed run).
 */
fun ComposeTestRule.screenSummary(): String {
    val parts = ArrayList<String>()
    fun walk(node: SemanticsNode) {
        node.config.getOrNull(SemanticsProperties.Text)?.let { texts -> parts += texts.joinToString("") { it.text } }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { parts += "[" + it.joinToString("/") + "]" }
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { parts += "#$it" }
        node.children.forEach(::walk)
    }
    return try {
        walk(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        parts.joinToString(" | ")
    } catch (e: Throwable) {
        "(could not read the screen: ${e.message})"
    }
}

/** Waits until [text] is on screen; on timeout the error lists what the screen showed instead. */
fun ComposeTestRule.waitForTextOrExplain(text: String, substring: Boolean = false, timeoutMillis: Long = 15_000) {
    try {
        waitUntil(timeoutMillis) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("never saw \"$text\"; the screen shows: ${screenSummary()}", e)
    }
}
