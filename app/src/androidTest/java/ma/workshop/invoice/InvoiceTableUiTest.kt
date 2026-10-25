package ma.workshop.invoice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * The compact invoice table on the item list, the review and the invoice detail, the paid badge on the
 * review, and the stacked buttons of the Saved screen. One door, 1.20 x 2.10 m at 500 DH/m2 = 1 260 DH.
 */
@RunWith(AndroidJUnit4::class)
class InvoiceTableUiTest {

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

    private fun click(text: String) {
        rule.waitForTextOrExplain(text)
        rule.onNodeWithText(text).performClick()
    }

    private fun keys(vararg keys: String) {
        try {
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("key-${keys.first()}").fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("no keypad; screen: ${rule.screenSummary()}", e)
        }
        for (key in keys) rule.onNodeWithTag("key-$key").performClick()
    }

    private fun addTheDoor() {
        click(Strings.NEW_INVOICE)
        click(Strings.CONTINUE)
        click(Strings.ADD_PRODUCT)
        click("باب حديد")
        rule.waitForTextOrExplain(Strings.WIDTH)
        keys("1", "decimal", "2", "0")
        rule.onNodeWithTag("key-next").performClick()
        keys("2", "decimal", "1", "0")
        rule.onNodeWithTag("key-next").performClick()
        keys("5", "0", "0")
        rule.onNodeWithTag("entry-continue").performClick()
        rule.waitForTextOrExplain(Strings.CONFIRM_ITEM_TITLE)
        click(Strings.CONFIRM)
        // the empty list has the add button too: wait for the table itself
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("invoice-table").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Amount on the left, then the calculation, then the quantity, and the product on the right. */
    private fun assertTableColumnOrder() {
        val amount = rule.onNodeWithTag("table-amount-0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val formula = rule.onNodeWithTag("table-formula-0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val quantity = rule.onNodeWithTag("table-quantity-0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val name = rule.onNodeWithTag("table-name-0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("amount left of the calculation", amount.right <= formula.left)
        assertTrue("calculation left of the quantity", formula.right <= quantity.left)
        assertTrue("quantity left of the product", quantity.right <= name.left)
    }

    @Test
    fun itemListShowsTheCompactTableWithAmountLeftAndProductRight() {
        addTheDoor()
        rule.onNodeWithTag("invoice-table").assertIsDisplayed()
        assertTableColumnOrder()
        // the unit price is inside the calculation cell and the quantity is not
        rule.onNodeWithText("2,52 م²", substring = true).assertIsDisplayed()
        // the actions sit below the row: delete on the left, edit on the right
        val delete = rule.onNodeWithText(Strings.DELETE).getUnclippedBoundsInRoot()
        val edit = rule.onNodeWithText(Strings.EDIT).getUnclippedBoundsInRoot()
        assertTrue(delete.right <= edit.left)
    }

    @Test
    fun reviewShowsTheSameTableAndThePaidBadgeOnlyWhenFullyPaid() {
        addTheDoor()
        click(Strings.CONTINUE)
        rule.waitForTextOrExplain(Strings.REVIEW_TITLE)
        rule.onNodeWithTag("invoice-table").assertIsDisplayed()
        assertTableColumnOrder()
        rule.onNodeWithTag("paid-badge").assertDoesNotExist()

        // deposit equals the total: "0 DH" with the badge on the same line
        rule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("deposit-field"))).performScrollTo().performClick()
        rule.waitForIdle()
        keys("1", "2", "6", "0")
        rule.waitForIdle()
        rule.onNodeWithTag("paid-badge").performScrollTo().assertIsDisplayed()
        val badge = rule.onNodeWithTag("paid-badge").getUnclippedBoundsInRoot()
        val balance = rule.onNodeWithTag("balance-value").getUnclippedBoundsInRoot()
        assertTrue("badge is left of the balance", badge.right <= balance.left)
        assertTrue("same line", kotlin.math.abs(badge.top.value + badge.bottom.value - balance.top.value - balance.bottom.value) < 40f)
        rule.onNodeWithTag("balance-value").assertIsDisplayed()
    }

    @Test
    fun savedScreenStacksItsTwoButtonsFullWidth_andDetailShowsTheTable() {
        addTheDoor()
        click(Strings.CONTINUE)
        click(Strings.SAVE_INVOICE)
        click(Strings.SAVE)
        rule.waitForTextOrExplain(Strings.SAVED_TITLE)

        val newInvoice = rule.onNodeWithTag("saved-new-invoice").getUnclippedBoundsInRoot()
        val home = rule.onNodeWithTag("saved-home").getUnclippedBoundsInRoot()
        assertEquals("same left edge", newInvoice.left.value, home.left.value, 0.5f)
        assertEquals("same width", (newInvoice.right - newInvoice.left).value, (home.right - home.left).value, 0.5f)
        assertTrue("home is under new invoice", home.top >= newInvoice.bottom)

        // history -> detail has the same table
        click(Strings.HOME)
        click(Strings.PREVIOUS_INVOICES)
        rule.waitForTextOrExplain(Strings.HISTORY_TITLE)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTextCount("0001") > 0
        }
        rule.onNodeWithText("0001", substring = true).performClick()
        rule.waitForTextOrExplain(Strings.INVOICE_DETAILS)
        rule.onNodeWithTag("invoice-table").performScrollTo().assertIsDisplayed()
        assertTableColumnOrder()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
}
