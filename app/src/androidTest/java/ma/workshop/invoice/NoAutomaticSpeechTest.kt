package ma.workshop.invoice

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.ui.Strings
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Nothing is spoken automatically (owner decision, 2026-11-01): with audio on and a voice ready, opening the
 * item entry screen, confirming a line and opening the review screen produce no utterance. A speaker tap
 * still speaks, which also proves the counter would have caught an automatic one.
 *
 * Needs an offline Arabic voice on the test device; without one the test is skipped, not passed.
 */
@RunWith(AndroidJUnit4::class)
class NoAutomaticSpeechTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private fun click(text: String) {
        rule.waitForTextOrExplain(text)
        rule.onNodeWithText(text).performClick()
    }

    @Test
    fun entryAndReviewScreensStaySilentWithAudioOnAndASpeakerTapStillSpeaks() {
        val container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            // Both old "speak totals" settings stay on: the behaviour they used to switch on must not come back.
            container.settings.update {
                it.copy(audioEnabled = true, showSpeakers = true, speakItemTotals = true, speakInvoiceTotals = true)
            }
            container.products.ensureSeeded()
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { container.speech.ensureStarted() }
        val deadline = System.currentTimeMillis() + 15_000
        while (!container.speech.isReady && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assumeTrue("needs an offline Arabic voice on the test device", container.speech.isReady)
        val before = container.speech.utteranceCount

        ActivityScenario.launch(MainActivity::class.java).use {
            click(Strings.NEW_INVOICE)
            click(Strings.CONTINUE)
            click(Strings.ADD_PRODUCT)
            click(Strings.CUSTOM_CHARGE)
            rule.waitForTextOrExplain(Strings.CONFIRM)
            assertEquals("opening item entry speaks nothing", before, container.speech.utteranceCount)

            rule.onNodeWithTag("charge-description").performTextReplacement("تركيب الباب")
            rule.enterNumber("field-rate", "350")
            rule.confirmEntry()
            click(Strings.CONTINUE)
            rule.waitForTextOrExplain(Strings.REVIEW_TITLE)
            rule.waitForTextOrExplain(Strings.SAVE_INVOICE)
            rule.waitForIdle()
            Thread.sleep(2_000) // anything delayed would have started by now
            assertEquals("confirming a line and opening review speak nothing", before, container.speech.utteranceCount)

            rule.onAllNodesWithContentDescription(Strings.REPLAY).onFirst().performClick()
            rule.waitForIdle()
            assertEquals("a speaker tap speaks", before + 1, container.speech.utteranceCount)
        }
    }
}
