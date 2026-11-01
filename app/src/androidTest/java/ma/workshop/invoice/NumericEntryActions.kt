package ma.workshop.invoice

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement

fun ComposeTestRule.showEntryControl(tag: String) {
    onNodeWithTag("entry-list").performScrollToNode(hasTestTag(tag))
}

/** Exercise real editable fields and the keyboard's Done action, including off-screen variations. */
fun ComposeTestRule.enterNumber(tag: String, text: String) {
    if (onAllNodesWithTag("entry-list").fetchSemanticsNodes().isNotEmpty()) showEntryControl(tag)
    else onNodeWithTag(tag).performScrollTo()
    onNodeWithTag(tag).performTextReplacement(text)
    onNodeWithTag(tag).performImeAction()
    waitForIdle()
}

fun ComposeTestRule.confirmEntry() {
    showEntryControl("confirm-item")
    onNodeWithTag("confirm-item").performClick()
}
