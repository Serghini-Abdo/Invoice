package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.backup.toDomain
import ma.workshop.invoice.domain.backup.toDto
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.format.InvoiceDocumentBuilder
import ma.workshop.invoice.domain.format.ItemDescriber
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.UnitLabelScript
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnitScriptTest {

    private val lri = ArabicFormat.LRI
    private val pdi = ArabicFormat.PDI
    private val nb = ArabicFormat.NBSP

    private val door = Fixtures.invoice(now = 1_792_929_600_000).items[0].pricing
    private val hinges = Fixtures.invoice(now = 1_792_929_600_000).items[1].pricing

    @Test
    fun `arabic labels are the default and need no isolate`() {
        assertEquals("1,20 × 2,10 م", ItemDescriber.measures(door))
        assertEquals("1,20 × 2,10 م", ItemDescriber.measures(door, UnitLabelScript.ARABIC))
        assertEquals("2,52 م² × 500${nb}DH", ItemDescriber.formula(door))
        assertFalse(ItemDescriber.rate(door).contains(lri))
    }

    @Test
    fun `latin labels change the letters only, never the digits, and sit in a left to right isolate`() {
        val latin = UnitLabelScript.LATIN
        assertEquals("${lri}1,20 × 2,10 m$pdi", ItemDescriber.measures(door, latin))
        assertEquals("${lri}2,52 m² × 500${nb}DH$pdi", ItemDescriber.formula(door, latin))
        assertEquals("${lri}500${nb}DH / m²$pdi", ItemDescriber.rate(door, latin))
        assertEquals("2,52 m²", ItemDescriber.baseAmount(door, latin))
        assertEquals("cm", ArabicFormat.unit(LengthUnit.CENTIMETER, latin))
        assertEquals("سم", ArabicFormat.unit(LengthUnit.CENTIMETER))
    }

    @Test
    fun `per piece items keep their arabic word for piece and need no isolate`() {
        val latin = UnitLabelScript.LATIN
        assertEquals("30${nb}DH / قطعة", ItemDescriber.formula(hinges, latin))
        assertEquals(null, ItemDescriber.measures(hinges, latin))
    }

    @Test
    fun `the printed document and its re exports follow the label script of the settings`() {
        val invoice = Fixtures.invoice(now = 1_792_929_600_000)
        val zone = ZoneId.of("Africa/Casablanca")
        val arabic = InvoiceDocumentBuilder.build(invoice, Fixtures.settings(), zone)
        val latin = InvoiceDocumentBuilder.build(invoice, Fixtures.settings().copy(unitLabelScript = UnitLabelScript.LATIN), zone)
        assertEquals("1,20 × 2,10 م", arabic.rows[0].measures)
        assertEquals("${lri}1,20 × 2,10 m$pdi", latin.rows[0].measures)
        assertEquals("${lri}2,52 m² × 500${nb}DH$pdi", latin.rows[0].formula)
        // the totals are untouched
        assertEquals(arabic.total, latin.total)
        assertEquals(arabic.rows[0].totalNumber, latin.rows[0].totalNumber)
    }

    @Test
    fun `a centimetre invoice reads in latin too and the breakdown is isolated line by line`() {
        val latin = UnitLabelScript.LATIN
        val lines = ItemDescriber.breakdown(door, latin)
        assertTrue(lines.first { it.label == "العرض" }.value.startsWith(lri))
        assertTrue(lines.first { it.label == "العرض" }.value.contains("m"))
    }

    @Test
    fun `settings carry the unit and the label script in backups, with defaults for old backups`() {
        val settings = AppSettings(lengthUnit = LengthUnit.CENTIMETER, unitLabelScript = UnitLabelScript.LATIN)
        val back = settings.toDto().toDomain()!!
        assertEquals(LengthUnit.CENTIMETER, back.lengthUnit)
        assertEquals(UnitLabelScript.LATIN, back.unitLabelScript)

        // an older backup has neither field: metres and Arabic labels
        val old = AppSettings().toDto().copy(lengthUnit = "unknown", unitLabelScript = "unknown").toDomain()!!
        assertEquals(LengthUnit.METER, old.lengthUnit)
        assertEquals(UnitLabelScript.ARABIC, old.unitLabelScript)
        assertEquals(LengthUnit.METER, AppSettings().lengthUnit)
    }
}
