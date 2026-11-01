package ma.workshop.invoice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.Measurements
import ma.workshop.invoice.domain.settings.UnitLabelScript
import ma.workshop.invoice.ui.Strings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One global unit: the entry shows only the unit of Settings (no m / cm switch) and the labels follow the script. */
@RunWith(AndroidJUnit4::class)
class UnitsUiTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(unit: LengthUnit, script: UnitLabelScript) {
        container = ApplicationProvider.getApplicationContext<InvoiceApp>().container
        runBlocking {
            container.database.clearAllTables()
            container.settings.update { it.copy(audioEnabled = false, lengthUnit = unit, unitLabelScript = script) }
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

    private fun openDoorEntry() {
        click(Strings.NEW_INVOICE)
        click(Strings.CONTINUE)
        click(Strings.ADD_PRODUCT)
        click("باب حديد")
        rule.waitForTextOrExplain(Strings.WIDTH)
    }

    private fun count(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun centimetresWithArabicLabelsHaveNoSwitchAndPriceTheSameDoor() {
        launch(LengthUnit.CENTIMETER, UnitLabelScript.ARABIC)
        openDoorEntry()
        // only the unit of Settings is written beside the dimensions; the other unit is not offered
        assertEquals(2, rule.onAllNodesWithText(Strings.CM_UNIT).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(Strings.METER_UNIT).fetchSemanticsNodes().size)

        rule.enterNumber("field-width", "120")
        rule.enterNumber("field-height", "210")
        rule.enterNumber("field-rate", "500")
        rule.showEntryControl("confirm-item")
        // 120 cm x 210 cm at 500 DH/m2 is the same 1 260 DH as 1.20 x 2.10 m
        rule.waitForTextOrExplain("1", substring = true)
        assertTrue(count("260") > 0)
        rule.confirmEntry()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("invoice-table").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the list writes the measures in centimetres", count("120 × 210 سم") > 0)
    }

    @Test
    fun latinLabelsReplaceTheArabicOnesOnTheEntryScreen() {
        launch(LengthUnit.METER, UnitLabelScript.LATIN)
        openDoorEntry()
        assertEquals(2, rule.onAllNodesWithText("m").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(Strings.METER_UNIT).fetchSemanticsNodes().size)
        rule.onNodeWithText("DH / m²").assertIsDisplayed()
    }

    @Test
    fun settingsOffersTheTwoChoices() {
        launch(LengthUnit.METER, UnitLabelScript.ARABIC)
        click(Strings.SETTINGS)
        rule.waitForTextOrExplain(Strings.UNITS)
        rule.onNodeWithTag("unit-cm").performScrollTo().performClick()
        rule.onNodeWithTag("script-latin").performScrollTo().performClick()
        rule.waitUntil(10_000) {
            runBlocking { container.settings.get().let { it.lengthUnit == LengthUnit.CENTIMETER && it.unitLabelScript == UnitLabelScript.LATIN } }
        }
        val settings = runBlocking { container.settings.get() }
        assertEquals(LengthUnit.CENTIMETER, settings.lengthUnit)
        assertEquals(UnitLabelScript.LATIN, settings.unitLabelScript)
    }

    @Test
    fun perPieceProductCanShowWidthWithoutUsingItInThePrice() {
        launch(LengthUnit.METER, UnitLabelScript.ARABIC)
        click(Strings.SETTINGS)
        click(Strings.PRODUCT_CATALOG)
        rule.onNodeWithText("مفصلات").performScrollTo().performClick()
        rule.waitForTextOrExplain(Strings.EDIT_PRODUCT)
        rule.onNodeWithTag("show-measurements").performScrollTo().performClick()
        rule.onNodeWithText(Strings.WIDTH).performScrollTo().performClick()
        click(Strings.SAVE)
        rule.waitForTextOrExplain(Strings.CATALOG_TITLE)
        val product = runBlocking { container.database.products().getAll().first { it.name == "مفصلات" } }
        assertEquals(Measurements.WIDTH.id, product.measurements)
        repeat(2) { rule.onNodeWithContentDescription(Strings.BACK).performClick(); rule.waitForIdle() }
        click(Strings.NEW_INVOICE)
        click(Strings.CONTINUE)
        click(Strings.ADD_PRODUCT)
        click("مفصلات")
        rule.enterNumber("field-width", "1.2")
        rule.enterNumber("field-rate", "800")
        rule.enterNumber("field-quantity", "2")
        rule.onNodeWithTag("field-height").assertDoesNotExist()
        rule.confirmEntry()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("invoice-table").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(count("600") > 0) // 1 600 DH, without multiplying by 1.2 m
    }
}
