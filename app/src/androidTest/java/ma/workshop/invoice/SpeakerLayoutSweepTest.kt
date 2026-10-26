package ma.workshop.invoice

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.ui.Strings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With the speaker icons on, the speakers and tiles take room. This walks the whole invoice flow and fails on any
 * screen where a word is broken in the middle (a label squeezed so far that it wraps inside a word).
 */
@RunWith(AndroidJUnit4::class)
class SpeakerLayoutSweepTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update { it.copy(audioEnabled = false, showSpeakers = true) }
            container.products.ensureSeeded()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** Texts (first 40 characters) that wrap in the middle of a word, anywhere on the current screen. */
    private fun ComposeTestRule.brokenWords(): List<String> {
        val broken = ArrayList<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
                val results = ArrayList<TextLayoutResult>()
                if (action(results) && results.isNotEmpty()) {
                    val result = results.first()
                    val text = result.layoutInput.text.text
                    for (line in 0 until result.lineCount - 1) {
                        val end = result.getLineEnd(line, visibleEnd = false)
                        if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) {
                            broken += text.take(40)
                        }
                    }
                }
            }
            node.children.forEach(::walk)
        }
        // a dialog is a second root next to the screen
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::walk)
        return broken.distinct()
    }

    private fun check(screen: String) {
        rule.waitForIdle()
        val broken = rule.brokenWords()
        assertTrue("$screen: words broken in the middle: $broken", broken.isEmpty())
    }

    private fun click(text: String) {
        rule.waitForTextOrExplain(text)
        rule.onNodeWithText(text).performClick()
    }

    private fun keys(vararg keys: String) {
        for (key in keys) rule.onNodeWithTag("key-$key").performClick()
    }

    @Test
    fun noWordIsBrokenOnAnyScreenOfTheInvoiceFlow() {
        rule.waitForTextOrExplain(Strings.NEW_INVOICE)
        check("home")

        click(Strings.NEW_INVOICE)
        rule.waitForTextOrExplain(Strings.CUSTOMER_TITLE)
        check("customer")
        click(Strings.CONTINUE)

        rule.waitForTextOrExplain(Strings.ADD_FIRST_PRODUCT)
        check("empty item list")
        click(Strings.ADD_PRODUCT)

        rule.waitForTextOrExplain(Strings.CHOOSE_PRODUCT)
        check("catalogue")
        click("باب حديد")

        rule.waitForTextOrExplain(Strings.WIDTH)
        check("item entry, empty")
        keys("1", "decimal", "2", "0")
        rule.onNodeWithTag("key-next").performClick()
        keys("2", "decimal", "1", "0")
        rule.onNodeWithTag("key-next").performClick()
        keys("5", "0", "0")
        check("item entry, filled")
        rule.onNodeWithTag("entry-continue").performClick()

        rule.waitForTextOrExplain(Strings.CONFIRM_ITEM_TITLE)
        check("item confirmation")
        // with the speaker tiles the two buttons are stacked, each full width, confirm above cancel
        val confirm = rule.onNodeWithTag("confirm-item").getUnclippedBoundsInRoot()
        val cancel = rule.onNodeWithTag("cancel-item").getUnclippedBoundsInRoot()
        assertTrue("confirm is above cancel", confirm.bottom <= cancel.top)
        assertEquals("same width", (confirm.right - confirm.left).value, (cancel.right - cancel.left).value, 0.5f)
        assertTrue("wide enough for its label", (confirm.right - confirm.left).value > 250f)
        click(Strings.CONFIRM)

        rule.waitUntil(15_000) { rule.onAllNodesWithTag("invoice-table").fetchSemanticsNodes().isNotEmpty() }
        check("item list")
        click(Strings.CONTINUE)

        rule.waitForTextOrExplain(Strings.REVIEW_TITLE)
        check("review")
        click(Strings.SAVE_INVOICE)
        rule.waitForTextOrExplain(Strings.CONFIRM_SAVE_TITLE)
        check("save dialog")
        click(Strings.SAVE)

        rule.waitForTextOrExplain(Strings.SAVED_TITLE)
        rule.waitForTextOrExplain(Strings.SHARE_IMAGE)
        check("saved")

        click(Strings.HOME)
        click(Strings.PREVIOUS_INVOICES)
        rule.waitForTextOrExplain(Strings.HISTORY_TITLE)
        rule.waitForTextOrExplain("0001", substring = true)
        check("history")
        rule.onNodeWithText("0001", substring = true).performClick()
        rule.waitForTextOrExplain(Strings.INVOICE_DETAILS)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("invoice-table").fetchSemanticsNodes().isNotEmpty() }
        check("invoice detail")
    }

    @Test
    fun noWordIsBrokenInSettingsAndTheProductScreens() {
        rule.waitForTextOrExplain(Strings.SETTINGS)
        click(Strings.SETTINGS)
        rule.waitForTextOrExplain(Strings.SHOW_SPEAKERS)
        check("settings")
    }
}
