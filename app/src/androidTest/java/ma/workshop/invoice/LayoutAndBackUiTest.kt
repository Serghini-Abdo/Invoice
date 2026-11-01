package ma.workshop.invoice

import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

/** Checks the forced right-to-left layout, touch-target sizes and the system Back button on the real UI. */
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
            container.settings.update { it.copy(audioEnabled = false, showSpeakers = true) }
            container.products.ensureSeeded()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun waitForText(text: String) = rule.waitForTextOrExplain(text)

    private fun keyboardVisible(): Boolean {
        var visible = false
        scenario!!.onActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun pressBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /**
     * The system Back button. On the entry screens, Back while the keyboard is open only closes the keyboard
     * (by design), so when it is open the first press is that one and the real Back follows it. A real phone
     * keeps its keyboard up after typing a width; an emulator without one never needed this.
     */
    private fun systemBack() {
        if (keyboardVisible()) {
            pressBack()
            rule.waitUntil(10_000) { !keyboardVisible() }
        }
        pressBack()
    }

    private fun goToCatalog() {
        goToItemList()
        rule.onNodeWithText(Strings.ADD_PRODUCT).performClick()
        waitForText(Strings.CHOOSE_PRODUCT)
    }

    private fun goToItemList() {
        waitForText(Strings.NEW_INVOICE)
        rule.onNodeWithText(Strings.NEW_INVOICE).performClick()
        waitForText(Strings.CONTINUE)
        rule.onNodeWithText(Strings.CONTINUE).performClick()
        waitForText(Strings.ADD_PRODUCT)
    }

    private fun rootWidth() = rule.onRoot().getBoundsInRoot().width

    @Test
    fun theLayoutIsRightToLeftWithTheBackArrowAtTheRightEdgeAndTheTitleNextToIt() {
        waitForText(Strings.NEW_INVOICE)
        for (label in listOf(Strings.NEW_INVOICE, Strings.PREVIOUS_INVOICES, Strings.SETTINGS)) {
            rule.onNodeWithText(label).assertHeightIsAtLeast(100.dp)
        }
        rule.onNodeWithText(Strings.NEW_INVOICE).performClick()
        waitForText(Strings.CUSTOMER_TITLE)

        val width = rootWidth()
        val back = rule.onNodeWithContentDescription(Strings.BACK).getBoundsInRoot()
        assertTrue("the back arrow is on the right half, was $back", back.left > width / 2)
        assertTrue("the back arrow's right edge is near the screen's right edge, was $back of $width", back.right > width - 16.dp)
        val title = rule.onNodeWithText(Strings.CUSTOMER_TITLE).getBoundsInRoot()
        assertTrue("the title sits left of the back arrow, was $title", title.right <= back.left + 1.dp)
        assertTrue("the title is not pushed far from the arrow, was $title of $back", back.left - title.right < 24.dp)
        rule.onNodeWithContentDescription(Strings.BACK).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText(Strings.CONTINUE).assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun arabicLabelsAndTextFieldsAreRightAligned() {
        waitForText(Strings.NEW_INVOICE)
        rule.onNodeWithText(Strings.NEW_INVOICE).performClick()
        waitForText(Strings.CUSTOMER_TITLE)
        val width = rootWidth()

        val label = rule.onNodeWithText(Strings.CUSTOMER_NAME).getBoundsInRoot()
        assertTrue("the field label sits on the right, was $label of $width", label.left > width / 2)
        val hint = rule.onNodeWithText(Strings.CUSTOMER_HINT).getBoundsInRoot()
        assertTrue("the banner text reaches towards the right, was $hint of $width", hint.right > width * 3 / 4)

        rule.onNodeWithTag("customer-name").performTextInput("أحمد")
        rule.onNodeWithTag("customer-phone").performTextInput("0612345678")
        rule.waitForIdle()
        // Both typed values are right-aligned in their box: the text ends near the right edge.
        for (typed in listOf("أحمد", "0612345678")) {
            val box = rule.onNodeWithText(typed).getBoundsInRoot()
            assertTrue("\"$typed\" is a field filling the width, was $box of $width", box.right > width - 110.dp && box.left < width / 2)
        }
    }

    @Test
    fun theYellowAddButtonIsLargeWithItsOwnSpeakerTileOnTheLeft() {
        goToItemList()
        rule.onNodeWithTag("add-product").assertHeightIsAtLeast(64.dp)
        val button = rule.onNodeWithTag("add-product").getBoundsInRoot()
        val tile = rule.onNodeWithContentDescription("${Strings.SPEAK_LABEL}: ${Strings.ADD_PRODUCT}")
        tile.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val tileBounds = tile.getBoundsInRoot()
        assertTrue("the speaker tile is left of its button, tile $tileBounds, button $button", tileBounds.right <= button.left)
        assertTrue("the gap is small (4 dp), tile $tileBounds, button $button", button.left - tileBounds.right < 8.dp)
        assertTrue("tile and button have the same height", kotlin.math.abs(tileBounds.height.value - button.height.value) < 1f)

        // A tap on the tile only speaks (audio is off here): no product screen opens.
        tile.performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText(Strings.CHOOSE_PRODUCT).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("add-product").performClick()
        waitForText(Strings.CHOOSE_PRODUCT)
    }

    @Test
    fun itemScreensShowTheirPictureInTheCentreOfTheTopBar() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        val width = rootWidth()
        val icon = rule.onNodeWithTag("top-bar-icon").getBoundsInRoot()
        val centre = (icon.left + icon.right) / 2
        assertTrue("the picture is centred, was $icon of $width", kotlin.math.abs(centre.value - width.value / 2) < 2f)
        val title = rule.onAllNodesWithText("باب حديد").onFirst().getBoundsInRoot()
        assertTrue("the title stays in the right half, was $title, icon $icon", title.left >= icon.right)
        assertTrue("the back arrow is right of the picture", rule.onNodeWithContentDescription(Strings.BACK).getBoundsInRoot().left >= icon.right)

        rule.enterNumber("field-width", "1.2")
        rule.enterNumber("field-height", "2")
        rule.enterNumber("field-rate", "500")
        rule.showEntryControl("field-width")

        rule.onNodeWithTag("top-bar-icon").assertExists()
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
    fun aFingerThatSlidesOffAProductDoesNotSelectIt() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performTouchInput {
            down(center)
            moveBy(Offset(0f, 400f))
            up()
        }
        rule.waitForIdle()
        // a sweep is a scroll, not a tap: the catalog stays open and nothing was selected
        rule.onNodeWithText(Strings.CHOOSE_PRODUCT).assertExists()
        assertTrue(rule.onAllNodesWithText(Strings.WIDTH).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun nativeNumberFieldsAndConfirmationAreAccessible() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        for (field in listOf("width", "height", "rate", "quantity")) {
            rule.showEntryControl("field-$field")
            rule.onNodeWithTag("field-$field").assertWidthIsAtLeast(48.dp)
        }
        rule.showEntryControl("confirm-item")
        rule.onNodeWithTag("confirm-item").assertHeightIsAtLeast(64.dp)
        rule.onNodeWithTag("key-1").assertDoesNotExist()
    }

    @Test
    fun typingInNativeFieldsPreservesTheOtherValues() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        rule.enterNumber("field-width", "1.20")
        rule.enterNumber("field-height", "2.1")
        rule.enterNumber("field-rate", "500")
        rule.enterNumber("field-rate", "50")
        for ((tag, text) in listOf("field-width" to "1.20", "field-height" to "2.1", "field-rate" to "50")) {
            rule.showEntryControl(tag)
            rule.onNodeWithTag(tag).assert(hasText(text))
        }
        rule.enterNumber("field-rate", "")
        rule.onNodeWithTag("field-rate").assert(hasText(""))
    }

    @Test
    fun systemBackWalksBackThroughTheWorkflowWithoutLosingTheStep() {
        goToCatalog()
        systemBack() // catalog -> item list
        waitForText(Strings.ADD_PRODUCT)
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
        rule.enterNumber("field-width", "1")
        systemBack() // entry -> catalog (typed values are kept in the draft)
        waitForText(Strings.CHOOSE_PRODUCT)
        systemBack() // catalog -> item list
        systemBack() // item list -> customer
        systemBack() // customer: a half typed item exists, so the app must ask, not discard
        waitForText(Strings.LEAVE_TITLE)
        rule.onNodeWithText(Strings.KEEP_EDITING).performClick()
        waitForText(Strings.CUSTOMER_TITLE)
    }

    @Test
    fun theResumeDialogStacksFullWidthButtonsWithTheMainOneOnTopAndSpeakersThatOnlySpeak() {
        goToCatalog()
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        rule.enterNumber("field-width", "1")
        systemBack(); systemBack(); systemBack(); systemBack() // entry, catalogue, item list, customer
        waitForText(Strings.LEAVE_TITLE)
        rule.onNodeWithText(Strings.LEAVE_KEEP_DRAFT).performClick()
        waitForText(Strings.NEW_INVOICE)
        rule.onAllNodesWithText(Strings.NEW_INVOICE).onFirst().performClick()
        waitForText(Strings.RESUME_DRAFT_TITLE)

        val width = rootWidth()
        val main = rule.onNodeWithText(Strings.RESUME).getBoundsInRoot()
        assertTrue("the main button is wide, was $main of $width", main.width > width * 0.5f)
        // the second button is the one right under it (the home screen behind has the same words)
        val second = rule.onAllNodesWithText(Strings.START_NEW).fetchSemanticsNodes().map { it.boundsInRoot }
            .filter { it.top >= main.bottom.value - 1f && it.top - main.bottom.value < 40f }
        assertTrue("a second button sits under the main one", second.isNotEmpty())
        assertTrue("both have the same width", kotlin.math.abs(second.first().width - main.width.value) < 2f)

        // each button has a speaker, and a tap on it only speaks
        val speaker = rule.onNodeWithContentDescription("${Strings.SPEAK_LABEL}: ${Strings.RESUME}")
        speaker.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        speaker.performClick()
        rule.waitForIdle()
        rule.onNodeWithText(Strings.RESUME_DRAFT_TITLE).assertExists()
        rule.onNodeWithText(Strings.RESUME).assertExists()
    }
}
