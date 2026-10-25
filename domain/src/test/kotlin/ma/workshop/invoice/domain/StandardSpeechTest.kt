package ma.workshop.invoice.domain

import kotlinx.serialization.json.Json
import ma.workshop.invoice.domain.backup.SettingsDto
import ma.workshop.invoice.domain.backup.toDomain
import ma.workshop.invoice.domain.backup.toDto
import ma.workshop.invoice.domain.invoice.PaymentSummary
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.SpeechLanguage
import ma.workshop.invoice.domain.speech.DarijaSpeech
import ma.workshop.invoice.domain.speech.SpeechTexts
import ma.workshop.invoice.domain.speech.StandardSpeech
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class StandardSpeechTest {

    private val msa = StandardSpeech

    @Test
    fun `prices are spoken in standard Arabic`() {
        assertEquals("ألف ومائتان وستون درهم", msa.amount(Money(126_000)))
        assertEquals("درهمان", msa.amount(Money(200)))
        assertEquals("خمسة دراهم وخمسون سنتيم", msa.amount(Money(550)))
        assertEquals("خمسمائة درهم", msa.amount(Money(50_000)))
        assertEquals("سبعة عشر ألف وستة وستون درهم وثلاثون سنتيم", msa.amount(Money(1_706_630)))
    }

    @Test
    fun `quantities are spoken as plain numbers`() {
        assertEquals("واحد", msa.quantity(1))
        assertEquals("اثنان", msa.quantity(2))
        assertEquals("ثمانية", msa.quantity(8))
        assertEquals("اثنا عشر", msa.quantity(12))
    }

    @Test
    fun `measurements count metres and centimetres`() {
        assertEquals("متر واحد وعشرون سنتيمتر", msa.length(Fixtures.m("1.2")))
        assertEquals("متران وعشرة سنتيمترات", msa.length(Fixtures.m("2.1")))
        assertEquals("متران", msa.length(Fixtures.m("2")))
        assertEquals("ثمانون سنتيمتر", msa.length(Fixtures.m("0.8")))
        assertEquals("اثنان فاصلة ثلاثمائة وخمسة وسبعون متر", msa.length(Fixtures.m("2.375")))
        assertEquals("مائة وعشرون سنتيمتر", msa.length(Fixtures.cm("120")))
        assertEquals("مائة وعشرون فاصلة خمسة سنتيمتر", msa.length(Fixtures.cm("120.5")))
    }

    @Test
    fun `a row is one sentence`() {
        val door = Fixtures.pricing(PricingFormula.AREA, Fixtures.m("1.2"), Fixtures.m("2.1"), 2, "500")
        assertEquals(
            "باب حديد، متر واحد وعشرون سنتيمتر في متران وعشرة سنتيمترات، العدد اثنان، المجموع ألفان وخمسمائة وعشرون درهم",
            msa.itemLine("باب حديد", door),
        )
        val hinges = Fixtures.pricing(PricingFormula.PER_ITEM, quantity = 8, rate = "35")
        assertEquals("مفصلات، العدد ثمانية، المجموع مائتان وثمانون درهم", msa.itemLine("مفصلات", hinges))
    }

    @Test
    fun `the calculation card is one sentence, method first`() {
        val door = Fixtures.pricing(PricingFormula.AREA, Fixtures.m("1.2"), Fixtures.m("2.1"), 2, "500")
        assertEquals(
            "الحساب بالمتر المربع، العرض متر واحد وعشرون سنتيمتر، الارتفاع متران وعشرة سنتيمترات، " +
                "المساحة اثنان فاصلة اثنان وخمسون متر مربع، الثمن خمسمائة درهم للمتر المربع، العدد اثنان، " +
                "المجموع ألفان وخمسمائة وعشرون درهم",
            msa.calculation(door),
        )
        val frame = Fixtures.pricing(PricingFormula.PERIMETER, Fixtures.m("2"), Fixtures.m("1.1"), 1, "120")
        assertEquals(
            "الحساب بالمتر الطولي، العرض متران، الارتفاع متر واحد وعشرة سنتيمترات، المحيط ستة أمتار وعشرون سنتيمتر، " +
                "الثمن مائة وعشرون درهم للمتر، العدد واحد، المجموع سبعمائة وأربعة وأربعون درهم",
            msa.calculation(frame),
        )
        val hinges = Fixtures.pricing(PricingFormula.PER_ITEM, quantity = 8, rate = "35")
        assertEquals(
            "الحساب بالقطعة، الثمن خمسة وثلاثون درهم للقطعة، العدد ثمانية، المجموع مائتان وثمانون درهم",
            msa.calculation(hinges),
        )
    }

    @Test
    fun `the totals card is one sentence`() {
        assertEquals(
            "المجموع سبعة عشر ألف وستة وستون درهم وثلاثون سنتيم، التسبيق خمسة آلاف درهم، " +
                "الباقي اثنا عشر ألف وستة وستون درهم وثلاثون سنتيم",
            msa.paymentSummary(PaymentSummary(Money(1_706_630), Money(500_000), Money(1_206_630))),
        )
        assertEquals(
            "المجموع مائتان درهم، التسبيق مائتان درهم، الفاتورة خالص",
            msa.paymentSummary(PaymentSummary(Money(20_000), Money(20_000), Money.ZERO)),
        )
    }

    @Test
    fun `the language setting picks the sentences`() {
        assertSame(DarijaSpeech, SpeechTexts.of(SpeechLanguage.DARIJA))
        assertSame(StandardSpeech, SpeechTexts.of(SpeechLanguage.STANDARD))
        assertEquals(SpeechLanguage.DARIJA, AppSettings().speechLanguage)
        val total = Money(252_000)
        assertNotEquals(SpeechTexts.of(SpeechLanguage.DARIJA).amount(total), SpeechTexts.of(SpeechLanguage.STANDARD).amount(total))
        assertNotEquals(SpeechTexts.of(SpeechLanguage.DARIJA).voiceTest, SpeechTexts.of(SpeechLanguage.STANDARD).voiceTest)
    }

    @Test
    fun `the add-product tile speaks in the chosen language`() {
        assertEquals("زيد منتوج", SpeechTexts.of(SpeechLanguage.DARIJA).addProduct)
        assertEquals("إضافة منتوج", SpeechTexts.of(SpeechLanguage.STANDARD).addProduct)
    }

    @Test
    fun `speech language is backed up, and older backups restore in Darija`() {
        val json = Json { ignoreUnknownKeys = true }
        val standard = AppSettings(speechLanguage = SpeechLanguage.STANDARD).toDto()
        assertEquals(
            SpeechLanguage.STANDARD,
            json.decodeFromString(SettingsDto.serializer(), json.encodeToString(SettingsDto.serializer(), standard)).toDomain()?.speechLanguage,
        )
        val beforeTheSetting = """{"workshopName":"","workshopPhone":"","exportFormat":"png","audioEnabled":true,
            "speakItemTotals":true,"speakInvoiceTotals":true,"thankYouMessage":"","nextInvoiceNumber":3,"speechRate":"fast"}"""
        assertEquals(SpeechLanguage.DARIJA, json.decodeFromString(SettingsDto.serializer(), beforeTheSetting).toDomain()?.speechLanguage)
        assertEquals(SpeechLanguage.DARIJA, standard.copy(speechLanguage = "klingon").toDomain()?.speechLanguage)
    }
}
