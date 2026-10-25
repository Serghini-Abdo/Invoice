package ma.workshop.invoice

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.domain.flow.PendingItem
import ma.workshop.invoice.domain.invoice.ProductRef
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemEntryForm
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingEngine
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.PricingResult
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.ProductCard
import ma.workshop.invoice.ui.screens.ItemConfirmScreen
import ma.workshop.invoice.ui.theme.InvoiceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A speaker tap only speaks: it never selects, confirms or navigates. */
@RunWith(AndroidJUnit4::class)
class SpeakerTapTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun speakerOnAProductCardNeverSelectsTheProduct() {
        var selected = 0
        var spoken = 0
        rule.setContent {
            InvoiceTheme {
                ProductCard(name = "باب حديد", illustration = "door", onSelect = { selected++ }, onSpeak = { spoken++ }, speechAvailable = true)
            }
        }
        rule.onNodeWithContentDescription(Strings.SPEAK_NAME).performClick()
        rule.onNodeWithContentDescription(Strings.SPEAK_NAME).performClick()
        rule.runOnIdle {
            assertEquals(2, spoken)
            assertEquals(0, selected)
        }
        rule.onNodeWithText("باب حديد").performClick()
        rule.runOnIdle { assertEquals(1, selected) }
    }

    @Test
    fun replaySpeakerOnTheConfirmationNeverConfirmsOrLeaves() {
        val pricing = (
            PricingEngine.price(
                PricingFormula.AREA,
                Length(1200, LengthUnit.METER),
                Length(2100, LengthUnit.METER),
                2,
                Money(50_000),
            ) as PricingResult.Success
            ).pricing
        val pending = PendingItem(
            product = ProductRef(1, "باب حديد", "door", PricingFormula.AREA, LengthUnit.METER),
            form = ItemEntryForm.fromPricing(pricing),
            confirmedPricing = pricing,
        )
        var spoken = 0
        var leftScreen = 0
        rule.setContent {
            InvoiceTheme {
                ItemConfirmScreen(
                    pending = pending,
                    speechAvailable = true,
                    audioEnabled = true,
                    onToggleAudio = {},
                    onSpeakTotal = {},
                    onReplay = { spoken++ },
                    onConfirm = { leftScreen++ },
                    onCancel = { leftScreen++ },
                    onBack = { leftScreen++ },
                )
            }
        }
        rule.onNodeWithContentDescription(Strings.REPLAY).performClick()
        rule.runOnIdle {
            assertEquals(1, spoken)
            assertEquals(0, leftScreen)
        }
    }
}

/** With the voice switched off, every speaker is silent and the invoice flow carries on normally. */
@RunWith(AndroidJUnit4::class)
class VoiceOffFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun speakersStaySilentAndTheFlowStillWorks() {
        val container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update { it.copy(audioEnabled = false) }
            container.products.ensureSeeded()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForTextOrExplain(Strings.NEW_INVOICE)
            rule.onNodeWithText(Strings.NEW_INVOICE).performClick()
            rule.waitForTextOrExplain(Strings.CONTINUE)
            rule.onNodeWithText(Strings.CONTINUE).performClick()
            rule.waitForTextOrExplain("+ ${Strings.ADD_PRODUCT}")
            rule.onNodeWithText("+ ${Strings.ADD_PRODUCT}").performClick()
            rule.waitForTextOrExplain("باب حديد")

            // The speakers on the cards: no voice prompt, no navigation.
            rule.onAllNodesWithContentDescription(Strings.SPEAK_NAME).onFirst().performClick()
            rule.waitForIdle()
            assertTrue(
                "the voice download prompt must not appear when audio is off",
                rule.onAllNodesWithText(Strings.VOICE_MISSING_TITLE).fetchSemanticsNodes().isEmpty(),
            )
            assertTrue("still on the product picker", rule.onAllNodesWithText(Strings.CHOOSE_PRODUCT).fetchSemanticsNodes().isNotEmpty())

            // The rest of the flow is untouched.
            rule.onNodeWithText("باب حديد").performClick()
            rule.waitForTextOrExplain(Strings.WIDTH)
            rule.onNodeWithTag("field-width").assertExists()
        }
    }
}
