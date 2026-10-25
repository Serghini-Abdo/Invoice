package ma.workshop.invoice

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.ui.Strings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks right-to-left placement, touch-target sizes and the system Back button on the real UI. */
@RunWith(AndroidJUnit4::class)
class LayoutAndBackUiTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update { it.copy(audioEnabled = false) }
            container.products.ensureSeeded()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun waitForText(text: String) = rule.waitForTextOrExplain(text)

    private fun systemBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun goToCatalog() {
        waitForText(Strings.NEW_INVOICE)
        rule.onNodeWithText(Strings.NEW_INVOICE).performClick()
        waitForText(Strings.CONTINUE)
        rule.onNodeWithText(Strings.CONTINUE).performClick()
        waitForText("+ ${Strings.ADD_PRODUCT}")
        rule.onNodeWithText("+ ${Strings.ADD_PRODUCT}").performClick()
        waitForText(Strings.CHOOSE_PRODUCT)
    }

    @Test
    fun theBackArrowIsOnTheRightAndHomeButtonsAreLarge() {
        waitForText(Strings.NEW_INVOICE)
        for (label in listOf(Strings.NEW_INVOICE, Strings.PREVIOUS_INVOICES, Strings.SETTINGS)) {
            rule.onNodeWithText(label).assertHeightIsAtLeast(100.dp)
        }
        rule.onNodeWithText(Strings.NEW_INVOICE).performClick()
        waitForText(Strings.CUSTOMER_TITLE)

        val rootWidth = rule.onRoot().getBoundsInRoot().width
        val back = rule.onNodeWithContentDescription(Strings.BACK).getBoundsInRoot()
        assertTrue("in right-to-left the back arrow is at the right edge", back.left > rootWidth / 2)
        val title = rule.onNodeWithText(Strings.CUSTOMER_TITLE).getBoundsInRoot()
        assertTrue("the title sits left of the back arrow", title.right <= back.left + 1.dp)
        rule.onNodeWithContentDescription(Strings.BACK).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText(Strings.CONTINUE).assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun catalogIsATwoColumnGridStartingFromTheRightWithAnIndependentSpeaker() {
        goToCatalog()
        val door = rule.onNodeWithText("باب حديد").getBoundsInRoot()
        val window = rule.onNodeWithText("نافذة ألمنيوم").getBoundsInRoot()
        assertTrue("first product is on the right, the second on its left", door.left > window.left)
        assertTrue("two products share the first row", kotlin.math.abs(door.top.value - window.top.value) < 8f)

        val speakers = rule.onAllNodesWithContentDescription(Strings.SPEAK_NAME)
        val count = speakers.fetchSemanticsNodes().size
        var placed = 0
        for (index in 0 until count) {
            // Unclipped on purpose: a card half scrolled off the bottom has a clipped, shorter visible area.
            val bounds = speakers[index].getUnclippedBoundsInRoot()
            // The lazy grid composes a few cards ahead of the screen without placing them (empty bounds).
            if (bounds.width.value == 0f && bounds.height.value == 0f) continue
            placed++
            assertTrue("speaker #$index is at least 48dp wide, was ${bounds.width} at $bounds", bounds.width >= 48.dp)
            assertTrue("speaker #$index is at least 48dp tall, was ${bounds.height} at $bounds", bounds.height >= 48.dp)
        }
        assertTrue("every visible card has its own speaker (placed $placed of $count)", placed >= 4)
    }

    @Test
    fun tappingTheSpeakerDoesNotSelectTheProduct() {
        goToCatalog()
        rule.onAllNodesWithContentDescription(Strings.SPEAK_NAME).onFirst().performClick()
        rule.waitForIdle()
        // still on the catalog: nothing was selected and nothing navigated away
        rule.onNodeWithText(Strings.CHOOSE_PRODUCT).assertExists()
        assertTrue(rule.onAllNodesWithText(Strings.WIDTH).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun keypadKeysAreLargeTouchTargets() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        for (key in listOf("1", "5", "0", "decimal", "backspace", "next")) {
            rule.onNodeWithTag("key-$key").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    private fun assertField(tag: String, shown: String) {
        try {
            rule.onNodeWithTag(tag).assert(hasAnyDescendant(hasText(shown)))
        } catch (e: AssertionError) {
            throw AssertionError("field $tag should show \"$shown\"; the screen shows: ${rule.screenSummary()}", e)
        }
    }

    @Test
    fun typingOnTheKeypadFillsEachFieldWithoutWipingTheOthers() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)

        for (key in listOf("1", "decimal", "2", "0")) rule.onNodeWithTag("key-$key").performClick()
        assertField("field-width", "1,20")

        rule.onNodeWithTag("key-next").performClick()
        for (key in listOf("2", "decimal", "1")) rule.onNodeWithTag("key-$key").performClick()
        assertField("field-height", "2,1")
        assertField("field-width", "1,20") // typing the height must not touch the width

        rule.onNodeWithTag("key-next").performClick()
        for (key in listOf("5", "0", "0")) rule.onNodeWithTag("key-$key").performClick()
        assertField("field-rate", "500")
        assertField("field-width", "1,20")
        assertField("field-height", "2,1")

        rule.onNodeWithTag("key-backspace").performClick()
        assertField("field-rate", "50")
        rule.onNodeWithTag("key-clear").performClick()
        assertField("field-rate", "0") // empty shows the grey 0 placeholder
    }

    @Test
    fun systemBackWalksBackThroughTheWorkflowWithoutLosingTheStep() {
        goToCatalog()
        systemBack() // catalog -> item list
        waitForText("+ ${Strings.ADD_PRODUCT}")
        assertTrue(rule.onAllNodesWithText(Strings.CHOOSE_PRODUCT).fetchSemanticsNodes().isEmpty())

        systemBack() // item list -> customer
        waitForText(Strings.CUSTOMER_TITLE)

        systemBack() // customer, nothing entered -> leaves the workflow without a prompt
        waitForText(Strings.NEW_INVOICE)
    }

    @Test
    fun leavingWithUnsavedWorkAsksFirst() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        rule.onNodeWithTag("key-1").performClick()
        systemBack() // entry -> catalog (typed values are kept in the draft)
        waitForText(Strings.CHOOSE_PRODUCT)
        systemBack() // catalog -> item list
        systemBack() // item list -> customer
        systemBack() // customer: a half typed item exists, so the app must ask, not discard
        waitForText(Strings.LEAVE_TITLE)
        rule.onNodeWithText(Strings.KEEP_EDITING).performClick()
        waitForText(Strings.CUSTOMER_TITLE)
    }
}
