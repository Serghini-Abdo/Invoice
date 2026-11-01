package ma.workshop.invoice

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.ui.Strings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through every screen of the real app on the emulator and saves pictures (see [Shots]).
 * It is also a smoke test: every screen must be reachable and render without crashing.
 */
@SdkSuppress(minSdkVersion = 26) // Compose's captureToImage needs PixelCopy (API 26)
@RunWith(AndroidJUnit4::class)
class ScreenshotTourTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update {
                it.copy(audioEnabled = false, workshopName = "ورشة الأمل للحدادة", workshopPhone = "0522000000")
            }
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

    private fun shot(name: String) {
        rule.waitForIdle()
        Shots.saveScaled(name, rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun addButton() = Strings.ADD_PRODUCT

    private fun addItem(product: String, width: String, height: String, rate: String, plusClicks: Int = 0) {
        rule.onNodeWithText(addButton()).performClick()
        waitForText(Strings.CHOOSE_PRODUCT)
        rule.onNodeWithText(product).performClick()
        waitForText(Strings.WIDTH)
        rule.enterNumber("field-width", width)
        rule.enterNumber("field-height", height)
        rule.enterNumber("field-rate", rate)
        rule.enterNumber("field-quantity", (1 + plusClicks).toString())
        rule.showEntryControl("confirm-item")
    }

    @Test
    fun tourOfEveryScreen() {
        waitForText(Strings.NEW_INVOICE)
        shot("01-home")

        click(Strings.NEW_INVOICE)
        waitForText(Strings.CUSTOMER_TITLE)
        shot("02-customer")
        click(Strings.CONTINUE)

        waitForText(addButton())
        shot("03-items-empty")
        rule.onNodeWithText(addButton()).performClick()
        waitForText(Strings.CHOOSE_PRODUCT)
        shot("04-catalog")

        rule.onNodeWithText("باب حديد").performClick()
        waitForText(Strings.WIDTH)
        shot("05-entry-empty")
        rule.enterNumber("field-width", "1.20")
        rule.enterNumber("field-height", "2.10")
        rule.enterNumber("field-rate", "500")
        shot("06-entry-filled")
        rule.showEntryControl("confirm-item")
        shot("07-confirm-item")
        rule.confirmEntry()

        waitForText(addButton())
        // one unit for the whole invoice now: 150 x 200 cm is typed as 1.5 x 2 m (the same price)
        addItem("إطار حديد", "1.5", "2", "80", plusClicks = 2)
        rule.confirmEntry()
        waitForText(addButton())
        shot("08-items-list")

        click(Strings.CONTINUE)
        waitForText(Strings.REVIEW_TITLE)
        shot("09-review")
        click(Strings.SAVE_INVOICE)
        waitForText(Strings.CONFIRM_SAVE_TITLE)
        click(Strings.SAVE)

        waitForText(Strings.SAVED_TITLE)
        waitForText(Strings.SHARE_IMAGE)
        shot("10-saved-export")

        click(Strings.HOME)
        waitForText(Strings.PREVIOUS_INVOICES)
        click(Strings.PREVIOUS_INVOICES)
        waitForText(Strings.HISTORY_TITLE)
        waitForText(Strings.INVOICE_NUMBER, substring = true)
        shot("11-history")
        rule.onAllNodesWithText(Strings.INVOICE_NUMBER, substring = true)[0].performClick()
        waitForText(Strings.INVOICE_DETAILS)
        waitForText(Strings.REMAINING)
        shot("12-detail")

        captureSettingsScreens()
    }

    /** Restarts at the home screen to capture the remaining screens. */
    private fun captureSettingsScreens() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText(Strings.SETTINGS)
        click(Strings.SETTINGS)
        waitForText(Strings.SETTINGS_TITLE)
        shot("13-settings")
        for ((title, name) in listOf(
            Strings.WORKSHOP_INFO to "13a-settings-workshop",
            Strings.EXPORT_FORMAT to "13b-settings-format",
            Strings.UNITS to "13c-settings-units",
            Strings.AUDIO to "13d-settings-audio",
        )) {
            click(title)
            waitForText(title)
            shot(name)
            rule.onNodeWithContentDescription(Strings.BACK).performClick()
            waitForText(Strings.SETTINGS_TITLE)
        }
        click(Strings.PRODUCT_CATALOG)
        waitForText(Strings.CATALOG_TITLE)
        shot("14-catalog-management")
        click("باب حديد")
        waitForText(Strings.EDIT_PRODUCT)
        shot("15-product-edit")
    }

    @Test
    fun backupScreenRenders() {
        waitForText(Strings.SETTINGS)
        click(Strings.SETTINGS)
        waitForText(Strings.SETTINGS_TITLE)
        click(Strings.BACKUP)
        waitForText(Strings.BACKUP_TITLE)
        shot("16-backup")
    }
}
