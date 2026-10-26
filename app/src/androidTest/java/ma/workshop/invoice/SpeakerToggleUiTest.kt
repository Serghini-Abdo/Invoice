package ma.workshop.invoice

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.ui.Strings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The "show speaker icons" switch: off by default; on, titles and buttons get speakers; Settings never does. */
@RunWith(AndroidJUnit4::class)
class SpeakerToggleUiTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(showSpeakers: Boolean) {
        val container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update { it.copy(audioEnabled = false, showSpeakers = showSpeakers) }
            container.products.ensureSeeded()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun speakers() = rule.onAllNodesWithTag("round-speaker").fetchSemanticsNodes().size
    private fun tiles() = rule.onAllNodesWithContentDescription("${Strings.SPEAK_LABEL}: ${Strings.CONTINUE}").fetchSemanticsNodes().size

    private fun openCustomerScreen() {
        rule.waitForTextOrExplain(Strings.NEW_INVOICE)
        rule.onNodeWithText(Strings.NEW_INVOICE).performClick()
        rule.waitForTextOrExplain(Strings.CUSTOMER_TITLE)
    }

    @Test
    fun speakersAreHiddenByDefault() {
        launch(showSpeakers = false)
        openCustomerScreen()
        assertEquals(0, speakers())
        assertEquals(0, tiles())
    }

    @Test
    fun withTheSwitchOnTitlesAndButtonsGetSpeakersAndATapOnlySpeaks() {
        launch(showSpeakers = true)
        openCustomerScreen()
        assertTrue("the title has a round speaker", speakers() >= 1)
        assertEquals("the Continue button has its speaker tile", 1, tiles())

        // tapping the tile only speaks: the screen does not change
        rule.onAllNodesWithContentDescription("${Strings.SPEAK_LABEL}: ${Strings.CONTINUE}")[0].performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText(Strings.CUSTOMER_TITLE).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(Strings.ITEMS_TITLE).fetchSemanticsNodes().size)
    }

    @Test
    fun settingsNeverShowSpeakersAndHoldsTheSwitch() {
        launch(showSpeakers = true)
        rule.waitForTextOrExplain(Strings.SETTINGS)
        rule.onNodeWithText(Strings.SETTINGS).performClick()
        rule.waitForTextOrExplain(Strings.SHOW_SPEAKERS)
        assertEquals(0, speakers())
    }

    private fun openItemList() {
        openCustomerScreen()
        rule.onNodeWithText(Strings.CONTINUE).performClick()
        rule.waitForTextOrExplain(Strings.ADD_FIRST_PRODUCT)
    }

    private fun described(text: String) = rule.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun withTheSwitchOffNoSpeakerExistsAnywhereOnTheFlow() {
        launch(showSpeakers = false)
        rule.waitForTextOrExplain(Strings.NEW_INVOICE)
        assertEquals("home cards", 0, speakers())
        openItemList()
        assertEquals(0, speakers())
        assertEquals("the add button has no tile", 0, described("${Strings.SPEAK_LABEL}: ${Strings.ADD_PRODUCT}"))
        rule.onNodeWithText(Strings.ADD_PRODUCT).performClick()
        rule.waitForTextOrExplain(Strings.CHOOSE_PRODUCT)
        assertEquals("catalogue cards have no speaker", 0, described(Strings.SPEAK_NAME))
    }

    @Test
    fun withTheSwitchOnHomeCardsMessagesCataloguecardsAndTheDisabledContinueSpeak() {
        launch(showSpeakers = true)
        rule.waitForTextOrExplain(Strings.NEW_INVOICE)
        assertTrue("one speaker in each home card and the title", speakers() >= 3)
        openItemList()
        assertTrue("the empty-list message has its own speaker", speakers() >= 2)
        assertEquals("the add button keeps its tile", 1, described("${Strings.SPEAK_LABEL}: ${Strings.ADD_PRODUCT}"))
        assertEquals("the disabled continue still has a tile", 1, described("${Strings.SPEAK_LABEL}: ${Strings.CONTINUE}"))
        // a tap on the message speaker does not open the catalogue
        rule.onAllNodesWithTag("round-speaker")[1].performClick()
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithText(Strings.CHOOSE_PRODUCT).fetchSemanticsNodes().size)
        rule.onNodeWithText(Strings.ADD_PRODUCT).performClick()
        rule.waitForTextOrExplain(Strings.CHOOSE_PRODUCT)
        assertTrue("catalogue cards have their speaker", described(Strings.SPEAK_NAME) >= 4)
    }
}
