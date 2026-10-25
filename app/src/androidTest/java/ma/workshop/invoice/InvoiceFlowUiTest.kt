package ma.workshop.invoice

import androidx.compose.ui.test.assertCountEquals
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

    private fun waitForText(text: String, substring: Boolean = false) {
        rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        waitForText(text)
        rule.onNodeWithText(text).performClick()
    }

    private fun keys(vararg keys: String) {
        for (key in keys) rule.onNodeWithTag("key-$key").performClick()
    }

    private fun addButton() = "+ ${Strings.ADD_PRODUCT}"

    @Test
    fun homeHasExactlyTheThreeMainActions() {
        waitForText(Strings.NEW_INVOICE)
        rule.onNodeWithText(Strings.NEW_INVOICE).assertIsDisplayed()
        rule.onNodeWithText(Strings.PREVIOUS_INVOICES).assertIsDisplayed()
        rule.onNodeWithText(Strings.SETTINGS).assertIsDisplayed()
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
        keys("1", "decimal", "2", "0")
        rule.onNodeWithTag("key-next").performClick()
        keys("2", "decimal", "1", "0")
        rule.onNodeWithTag("key-next").performClick()
        keys("5", "0", "0")
        rule.onNodeWithTag("entry-continue").performClick()

        // Step 4: confirmation shows 1 260 DH
        waitForText(Strings.CONFIRM_ITEM_TITLE)
        val firstTotal = ArabicFormat.amount(Money(126_000))
        rule.onAllNodesWithText(firstTotal, substring = true)[0].assertIsDisplayed()
        click(Strings.CONFIRM)

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
        keys("1")
        rule.onNodeWithTag("key-next").performClick()
        keys("2")
        rule.onNodeWithTag("key-next").performClick()
        keys("6", "0", "0")
        rule.onNodeWithContentDescription(Strings.INCREASE).performScrollTo().performClick()
        rule.onNodeWithTag("entry-continue").performClick()
        waitForText(Strings.CONFIRM_ITEM_TITLE)
        click(Strings.CONFIRM)

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
