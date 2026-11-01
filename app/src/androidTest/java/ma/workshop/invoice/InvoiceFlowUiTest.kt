package ma.workshop.invoice

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.ui.Strings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real app through the workflow the spec describes, with audio switched off, and checks
 * the database afterwards. This is the navigation rule test: confirming an item lands on the item
 * list, "+" can add more, the same product can be added twice, and saving works.
 */
@RunWith(AndroidJUnit4::class)
class InvoiceFlowUiTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update { it.copy(audioEnabled = false) } // the app must be fully usable silently
            container.products.ensureSeeded()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun waitForText(text: String, substring: Boolean = false) = rule.waitForTextOrExplain(text, substring)

    private fun click(text: String) {
        waitForText(text)
        rule.onNodeWithText(text).performClick()
    }

    private fun addButton() = Strings.ADD_PRODUCT

    @Test
    fun homeHasExactlyTheThreeMainActions() {
        waitForText(Strings.NEW_INVOICE)
        rule.onNodeWithText(Strings.NEW_INVOICE).assertIsDisplayed()
        rule.onNodeWithText(Strings.PREVIOUS_INVOICES).assertIsDisplayed()
        rule.onNodeWithText(Strings.SETTINGS).assertIsDisplayed()
    }

    @Test
    fun fourDoorSizesKeepTheirPricesAndSaveAsIndependentLines() {
        click(Strings.NEW_INVOICE)
        click(Strings.CONTINUE)
        click(Strings.ADD_PRODUCT)
        click("باب حديد")
        for (index in 0..3) {
            val suffix = if (index == 0) "" else "-$index"
            rule.enterNumber("field-width$suffix", listOf("1", "1.1", "1.2", "1.3")[index])
            rule.enterNumber("field-height$suffix", "2")
            if (index == 0) rule.enterNumber("field-rate", "500")
            else {
                rule.showEntryControl("field-rate$suffix")
                rule.onNodeWithTag("field-rate$suffix").assert(hasText("500"))
                rule.onNodeWithTag("same-price-$index").assertExists()
            }
            if (index < 3) {
                rule.showEntryControl("add-variation")
                rule.onNodeWithTag("add-variation").performClick()
            }
        }
        rule.confirmEntry()
        click(Strings.CONTINUE)
        click(Strings.SAVE_INVOICE)
        click(Strings.SAVE)
        waitForText(Strings.SAVED_TITLE)
        runBlocking {
            val invoice = container.database.invoices().getAll().single()
            val items = container.database.invoices().getItems(invoice.id)
            assertEquals(4, items.map { it.id }.toSet().size)
            assertEquals(listOf(100000L, 110000L, 120000L, 130000L), items.map { it.totalCentimes })
            assertEquals(460000L, invoice.totalCentimes)
        }
    }

    @Test
    fun customLabourNeedsOnlyADescriptionAndAmount() {
        click(Strings.NEW_INVOICE)
        click(Strings.CONTINUE)
        click(Strings.ADD_PRODUCT)
        click(Strings.CUSTOM_CHARGE)
        rule.onNodeWithTag("charge-description").performTextReplacement("تركيب الباب")
        rule.enterNumber("field-rate", "350")
        rule.onNodeWithTag("field-width").assertDoesNotExist()
        rule.onNodeWithTag("field-height").assertDoesNotExist()
        rule.onNodeWithTag("field-quantity").assertDoesNotExist()
        rule.confirmEntry()
        click(Strings.CONTINUE)
        click(Strings.SAVE_INVOICE)
        click(Strings.SAVE)
        waitForText(Strings.SAVED_TITLE)
        runBlocking {
            val invoice = container.database.invoices().getAll().single()
            val item = container.database.invoices().getItems(invoice.id).single()
            assertEquals(null, item.productId)
            assertEquals("fixed", item.formula)
            assertEquals(35000L, item.totalCentimes)
        }
    }

    @Test
    fun keyboardCanBeDismissedWithoutLeavingOrLosingInput() {
        click(Strings.NEW_INVOICE)
        click(Strings.CONTINUE)
        click(Strings.ADD_PRODUCT)
        click("باب حديد")
        rule.onNodeWithTag("field-width").performClick().performTextReplacement("1.25")
        waitForText(Strings.HIDE_KEYBOARD)
        rule.onNodeWithTag("hide-keyboard").performClick()
        rule.onNodeWithTag("field-width").assert(hasText("1.25"))
        rule.onNodeWithText(Strings.CHOOSE_PRODUCT).assertDoesNotExist()
        rule.onNodeWithTag("field-width").performClick()
        waitForText(Strings.HIDE_KEYBOARD)
        rule.onNodeWithTag("field-width").performImeAction()
        rule.onNodeWithTag("field-width").assert(hasText("1.25"))
    }

    @Test
    fun fullWorkflowWithTheSameProductTwiceThenSave() {
        click(Strings.NEW_INVOICE)

        // Step 1: customer details are optional
        click(Strings.CONTINUE)

        // Step 2: empty list shows the add action
        waitForText(addButton())
        rule.onNodeWithText(addButton()).performClick()

        // Step 3: first door 1.20 x 2.10 m at 500 DH/m2, quantity untouched (default 1)
        waitForText(Strings.CHOOSE_PRODUCT)
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        rule.enterNumber("field-width", "1.20")
        rule.enterNumber("field-height", "2.10")
        rule.enterNumber("field-rate", "500")
        rule.showEntryControl("confirm-item")

        // Step 4: confirmation shows 1 260 DH
        val firstTotal = ArabicFormat.amount(Money(126_000))
        rule.onNodeWithTag("entry-total").assert(hasText(firstTotal, substring = true))
        rule.confirmEntry()

        // After confirming, we are on the item list: not the catalog, not payment
        waitForText(addButton())
        rule.onNodeWithText(Strings.CHOOSE_PRODUCT).assertDoesNotExist()
        rule.onNodeWithText(Strings.REVIEW_TITLE).assertDoesNotExist()
        rule.onAllNodesWithText("باب حديد").assertCountEquals(1)

        // "+" adds another; the same product again, different size, rate and quantity 2
        rule.onNodeWithText(addButton()).performClick()
        waitForText(Strings.CHOOSE_PRODUCT)
        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        rule.enterNumber("field-width", "1")
        rule.enterNumber("field-height", "2")
        rule.enterNumber("field-rate", "600")
        rule.enterNumber("field-quantity", "2")
        rule.showEntryControl("confirm-item")
        rule.confirmEntry()

        // two independent items on the list
        waitForText(addButton())
        rule.onAllNodesWithText("باب حديد").assertCountEquals(2)

        // Step 5: review then save
        click(Strings.CONTINUE)
        waitForText(Strings.REVIEW_TITLE)
        click(Strings.SAVE_INVOICE)
        waitForText(Strings.CONFIRM_SAVE_TITLE)
        click(Strings.SAVE)

        // Step 6: saved, export prepared, sharing offered
        waitForText(Strings.SAVED_TITLE)
        waitForText(Strings.SHARE_IMAGE)

        runBlocking {
            val invoices = container.database.invoices().getAll()
            assertEquals(1, invoices.size)
            val items = container.database.invoices().getItems(invoices[0].id)
            assertEquals(2, items.size)
            assertEquals(listOf(126_000L, 240_000L), items.map { it.totalCentimes }) // 1.2*2.1*500 ; 1*2*600*2
            assertEquals(listOf(1, 2), items.map { it.quantity })
            assertEquals(366_000L, invoices[0].totalCentimes)
            assertEquals(366_000L, invoices[0].remainingCentimes)
        }
    }
}
