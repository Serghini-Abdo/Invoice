package ma.workshop.invoice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.settings.ExportFormat
import ma.workshop.invoice.domain.settings.SpeechLanguage
import ma.workshop.invoice.domain.settings.SpeechRate
import ma.workshop.invoice.domain.settings.UnitLabelScript
import ma.workshop.invoice.ui.Strings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings is a list of six category cards; each opens its own screen; audio children are hidden while audio is off. */
@RunWith(AndroidJUnit4::class)
class SettingsCategoriesUiTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    private val cards = listOf("card-catalog", "card-workshop", "card-format", "card-units", "card-audio", "card-backup")

    private fun launch(update: (ma.workshop.invoice.domain.settings.AppSettings) -> ma.workshop.invoice.domain.settings.AppSettings) {
        container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update(update)
            container.products.ensureSeeded()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun click(text: String) {
        rule.waitForTextOrExplain(text)
        rule.onNodeWithText(text).performClick()
    }

    private fun openSettings() {
        click(Strings.SETTINGS)
        rule.waitForTextOrExplain(Strings.SETTINGS_TITLE)
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun back() {
        rule.onNodeWithContentDescription(Strings.BACK).performClick()
        rule.waitForIdle()
    }

    @Test
    fun theParentShowsSixCardsWithTheirValueLinesAndNeedsNoScrolling() {
        launch {
            it.copy(
                audioEnabled = false,
                workshopName = "ورشة الأمل",
                exportFormat = ExportFormat.PDF,
                lengthUnit = LengthUnit.CENTIMETER,
                unitLabelScript = UnitLabelScript.ARABIC,
            )
        }
        openSettings()
        // all six are on screen at once: nothing was scrolled to
        for (tag in cards) rule.onNodeWithTag(tag).assertIsDisplayed()

        val activeProducts = runBlocking { container.products.observeActive().first().size }
        rule.waitForTextOrExplain("$activeProducts ${Strings.PRODUCT_WORD}")
        rule.waitForTextOrExplain("ورشة الأمل")
        rule.waitForTextOrExplain(Strings.FORMAT_PDF)
        rule.waitForTextOrExplain("${Strings.CM_UNIT} · ${Strings.UNIT_LABELS_ARABIC}")
        rule.waitForTextOrExplain(Strings.AUDIO_VALUE_OFF)
    }

    @Test
    fun anUnnamedWorkshopSaysNotWritten() {
        launch { it.copy(audioEnabled = false, workshopName = "") }
        openSettings()
        rule.waitForTextOrExplain(Strings.NOT_WRITTEN)
    }

    @Test
    fun eachCategoryOpensItsScreenAndBackReturnsToTheList() {
        launch { it.copy(audioEnabled = false) }
        openSettings()
        for ((title, tag) in listOf(
            Strings.WORKSHOP_INFO to "workshop-name",
            Strings.EXPORT_FORMAT to null,
            Strings.UNITS to "unit-cm",
            Strings.AUDIO to null,
        )) {
            click(title)
            if (tag != null) rule.waitUntil(10_000) { rule.onAllNodesWithTagCount(tag) > 0 }
            // the child's top bar carries the same title as the card
            rule.waitForTextOrExplain(title)
            back()
            for (card in cards) rule.onNodeWithTag(card).assertIsDisplayed()
        }
    }

    @Test
    fun turningAudioOffHidesItsChildrenAndTurningItOnShowsThemWithTheirValues() {
        launch {
            it.copy(
                audioEnabled = true,
                showSpeakers = true,
                speechLanguage = SpeechLanguage.STANDARD,
                speechRate = SpeechRate.FAST,
                speakItemTotals = false,
            )
        }
        openSettings()
        click(Strings.AUDIO)
        rule.waitForTextOrExplain(Strings.SHOW_SPEAKERS)
        assertEquals(1, count(Strings.SPEECH_LANGUAGE))

        click(Strings.AUDIO_ENABLED) // off
        rule.waitForTextOrExplain(Strings.AUDIO_OFF)
        for (child in listOf(Strings.SHOW_SPEAKERS, Strings.SPEECH_LANGUAGE, Strings.SPEECH_RATE, Strings.DICTATE)) {
            assertEquals("\"$child\" is hidden, not greyed out", 0, count(child))
        }

        click(Strings.AUDIO_ENABLED) // on again
        rule.waitForTextOrExplain(Strings.SHOW_SPEAKERS)
        assertEquals(0, count(Strings.AUDIO_OFF))
        rule.waitForTextOrExplain(Strings.SPEECH_LANGUAGE)

        // hiding never reset a saved value
        val saved = runBlocking { container.settings.get() }
        assertEquals(SpeechLanguage.STANDARD, saved.speechLanguage)
        assertEquals(SpeechRate.FAST, saved.speechRate)
        assertFalse(saved.speakItemTotals)
        assertEquals(true, saved.showSpeakers)
    }

    @Test
    fun theCardValueFollowsTheAudioSettings() {
        launch { it.copy(audioEnabled = true, speechLanguage = SpeechLanguage.DARIJA, speechRate = SpeechRate.NORMAL) }
        openSettings()
        rule.waitForTextOrExplain("${Strings.AUDIO_VALUE_ON} · ${Strings.SPEECH_LANGUAGE_DARIJA} · ${Strings.SPEECH_RATE_NORMAL}")
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size
}
