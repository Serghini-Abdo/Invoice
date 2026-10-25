package ma.workshop.invoice.domain

import kotlinx.serialization.json.Json
import ma.workshop.invoice.domain.backup.SettingsDto
import ma.workshop.invoice.domain.backup.toDomain
import ma.workshop.invoice.domain.backup.toDto
import ma.workshop.invoice.domain.invoice.PaymentSummary
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.SpeechRate
import ma.workshop.invoice.domain.speech.DarijaNumbers
import ma.workshop.invoice.domain.speech.DarijaSpeech
import ma.workshop.invoice.domain.speech.DictationErrors
import ma.workshop.invoice.domain.speech.DictationProblem
import ma.workshop.invoice.domain.speech.DictationText
import ma.workshop.invoice.domain.speech.SpeechDecision
import ma.workshop.invoice.domain.speech.SpeechGate
import ma.workshop.invoice.domain.speech.VoiceState
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechTest {

    @Test
    fun `integers are spoken as Darija words`() {
        val cases = mapOf(
            0L to "صفر",
            1L to "واحد",
            2L to "جوج",
            8L to "تمنية",
            11L to "حضاش",
            12L to "طناش",
            19L to "تسعطاش",
            20L to "عشرين",
            21L to "واحد و عشرين",
            22L to "تنين و عشرين",
            35L to "خمسة و تلاتين",
            100L to "مية",
            101L to "مية و واحد",
            120L to "مية و عشرين",
            200L to "ميتين",
            250L to "ميتين و خمسين",
            999L to "تسع مية و تسعة و تسعين",
            1000L to "ألف",
            1260L to "ألف و ميتين و ستين",
            2000L to "ألفين",
            2520L to "ألفين و خمس مية و عشرين",
            3000L to "تلت آلاف",
            10_000L to "عشر آلاف",
            11_000L to "حضاش ألف",
            25_000L to "خمسة و عشرين ألف",
            100_000L to "مية ألف",
            17_066L to "سبعطاش ألف و ستة و ستين",
            1_000_000L to "مليون",
            2_000_000L to "جوج مليون",
            1_250_000L to "مليون و ميتين و خمسين ألف",
        )
        for ((n, words) in cases) assertEquals(words, DarijaNumbers.integer(n), "for $n")
    }

    @Test
    fun `prices are spoken in dirhams and centimes`() {
        assertEquals("ألف و ميتين و ستين درهم", DarijaSpeech.amount(Money(126_000)))
        assertEquals("درهم واحد", DarijaSpeech.amount(Money(100)))
        assertEquals("جوج دراهم", DarijaSpeech.amount(Money(200)))
        assertEquals("خمس دراهم", DarijaSpeech.amount(Money(500)))
        assertEquals("خمس دراهم و خمسين سنتيم", DarijaSpeech.amount(Money(550)))
        assertEquals("حضاش درهم", DarijaSpeech.amount(Money(1_100)))
        assertEquals("خمس مية درهم", DarijaSpeech.amount(Money(50_000)))
        assertEquals("خمسة و عشرين سنتيم", DarijaSpeech.amount(Money(25)))
        assertEquals("صفر درهم", DarijaSpeech.amount(Money.ZERO))
        assertEquals("سبعطاش ألف و ستة و ستين درهم و تلاتين سنتيم", DarijaSpeech.amount(Money(1_706_630)))
    }

    @Test
    fun `quantities are spoken as plain numbers`() {
        assertEquals("واحد", DarijaSpeech.quantity(1))
        assertEquals("جوج", DarijaSpeech.quantity(2))
        assertEquals("تمنية", DarijaSpeech.quantity(8))
        assertEquals("تسع آلاف و تسع مية و تسعة و تسعين", DarijaSpeech.quantity(9_999))
    }

    @Test
    fun `measurements are spoken the way the workshop says them`() {
        assertEquals("متر و عشرين سانتي", DarijaSpeech.length(Fixtures.m("1.2")))
        assertEquals("جوج متر و عشرة سانتي", DarijaSpeech.length(Fixtures.m("2.1")))
        assertEquals("جوج متر", DarijaSpeech.length(Fixtures.m("2")))
        assertEquals("متر", DarijaSpeech.length(Fixtures.m("1")))
        assertEquals("تمانين سانتي", DarijaSpeech.length(Fixtures.m("0.8")))
        assertEquals("جوج فاصلة تلت مية و خمسة و سبعين متر", DarijaSpeech.length(Fixtures.m("2.375")))
        assertEquals("مية و عشرين سانتي", DarijaSpeech.length(Fixtures.cm("120")))
        assertEquals("مية و عشرين فاصلة خمسة سانتي", DarijaSpeech.length(Fixtures.cm("120.5")))
        assertEquals("جوج فاصلة تنين و خمسين", DarijaNumbers.decimal(BigDecimal("2.52")))
        assertEquals("جوج فاصلة صفر خمسة", DarijaNumbers.decimal(BigDecimal("2.05")))
    }

    @Test
    fun `a row is one sentence with size, quantity and total`() {
        val door = Fixtures.pricing(PricingFormula.AREA, Fixtures.m("1.2"), Fixtures.m("2.1"), 2, "500")
        assertEquals(
            "باب حديد، متر و عشرين سانتي على جوج متر و عشرة سانتي، العدد جوج، المجموع ألفين و خمس مية و عشرين درهم",
            DarijaSpeech.itemLine("باب حديد", door),
        )
        val hinges = Fixtures.pricing(PricingFormula.PER_ITEM, quantity = 8, rate = "35")
        assertEquals("مفصلات، العدد تمنية، المجموع ميتين و تمانين درهم", DarijaSpeech.itemLine("مفصلات", hinges))
    }

    @Test
    fun `the calculation card is read as one sentence, method first`() {
        val door = Fixtures.pricing(PricingFormula.AREA, Fixtures.m("1.2"), Fixtures.m("2.1"), 2, "500")
        assertEquals(
            "الحساب بالمتر مربع، العرض متر و عشرين سانتي، الطول جوج متر و عشرة سانتي، " +
                "المساحة جوج فاصلة تنين و خمسين متر مربع، الثمن خمس مية درهم للمتر مربع، العدد جوج، " +
                "المجموع ألفين و خمس مية و عشرين درهم",
            DarijaSpeech.calculation(door),
        )
        val frame = Fixtures.pricing(PricingFormula.PERIMETER, Fixtures.m("2"), Fixtures.m("1.1"), 1, "120")
        assertEquals(
            "الحساب بالمتر الطولي، العرض جوج متر، الطول متر و عشرة سانتي، الدورة ستة متر و عشرين سانتي، " +
                "الثمن مية و عشرين درهم للمتر، العدد واحد، المجموع سبع مية و ربعة و ربعين درهم",
            DarijaSpeech.calculation(frame),
        )
        val hinges = Fixtures.pricing(PricingFormula.PER_ITEM, quantity = 8, rate = "35")
        assertEquals(
            "الحساب بالحبة، الثمن خمسة و تلاتين درهم للحبة، العدد تمنية، المجموع ميتين و تمانين درهم",
            DarijaSpeech.calculation(hinges),
        )
    }

    @Test
    fun `areas are spoken with the decimals shown on screen`() {
        val garage = Fixtures.pricing(PricingFormula.AREA, Fixtures.m("3.25"), Fixtures.m("2.375"), 1, "1250.50")
        // 7.71875 m² is shown as 7,7188.
        assertEquals(true, DarijaSpeech.calculation(garage).contains("المساحة سبعة فاصلة سبع آلاف و مية و تمنية و تمانين متر مربع"))
    }

    @Test
    fun `the totals card is read as one sentence`() {
        assertEquals(
            "المجموع ديال الفاتورة سبعطاش ألف و ستة و ستين درهم و تلاتين سنتيم، التسبيق خمس آلاف درهم، " +
                "الباقي طناش ألف و ستة و ستين درهم و تلاتين سنتيم",
            DarijaSpeech.paymentSummary(PaymentSummary(Money(1_706_630), Money(500_000), Money(1_206_630))),
        )
        assertEquals(
            "المجموع ديال الفاتورة ألف و ميتين و ستين درهم، ما كاين حتى تسبيق، الباقي ألف و ميتين و ستين درهم",
            DarijaSpeech.paymentSummary(PaymentSummary(Money(126_000), Money.ZERO, Money(126_000))),
        )
        assertEquals(
            "المجموع ديال الفاتورة ميتين درهم، التسبيق ميتين درهم، الفاتورة خالصة",
            DarijaSpeech.paymentSummary(PaymentSummary(Money(20_000), Money(20_000), Money.ZERO)),
        )
    }

    @Test
    fun `voice off never speaks and never asks to download a voice`() {
        val gate = SpeechGate()
        repeat(3) { assertEquals(SpeechDecision.AUDIO_OFF, gate.decide(audioEnabled = false, voice = VoiceState.MISSING)) }
        // The download explanation is still owed once audio is switched back on.
        assertEquals(SpeechDecision.ASK_FOR_VOICE, gate.decide(audioEnabled = true, voice = VoiceState.MISSING))
    }

    @Test
    fun `a missing voice is explained once, then speakers stay silent`() {
        val gate = SpeechGate()
        assertEquals(SpeechDecision.STILL_CHECKING, gate.decide(true, VoiceState.CHECKING))
        assertEquals(SpeechDecision.ASK_FOR_VOICE, gate.decide(true, VoiceState.MISSING))
        assertEquals(SpeechDecision.NO_VOICE, gate.decide(true, VoiceState.MISSING))
        assertEquals(SpeechDecision.SPEAK, gate.decide(true, VoiceState.READY))
    }

    @Test
    fun `recognizer errors map to a hint, and missing offline Arabic is recognised`() {
        assertEquals(DictationProblem.OFFLINE_PACK_MISSING, DictationErrors.problemFor(DictationErrors.NETWORK))
        assertEquals(DictationProblem.OFFLINE_PACK_MISSING, DictationErrors.problemFor(DictationErrors.LANGUAGE_UNAVAILABLE))
        assertEquals(DictationProblem.OFFLINE_PACK_MISSING, DictationErrors.problemFor(DictationErrors.LANGUAGE_NOT_SUPPORTED))
        assertEquals(DictationProblem.NO_MATCH, DictationErrors.problemFor(DictationErrors.NO_MATCH))
        assertEquals(DictationProblem.NO_MATCH, DictationErrors.problemFor(DictationErrors.SPEECH_TIMEOUT))
        assertEquals(DictationProblem.PERMISSION_DENIED, DictationErrors.problemFor(DictationErrors.INSUFFICIENT_PERMISSIONS))
        assertEquals(DictationProblem.BUSY, DictationErrors.problemFor(DictationErrors.RECOGNIZER_BUSY))
        assertEquals(DictationProblem.FAILED, DictationErrors.problemFor(DictationErrors.AUDIO))
    }

    @Test
    fun `recognised text is tidied and kept within the field limit`() {
        assertEquals("أحمد بناني", DictationText.forField("  أحمد   بناني \n", 60))
        assertEquals("باب", DictationText.forField("باب حديد", 4))
    }

    @Test
    fun `speech rate is backed up, and older backups restore at the normal rate`() {
        val json = Json { ignoreUnknownKeys = true }
        val withRate = AppSettings(speechRate = SpeechRate.FAST).toDto()
        assertEquals(SpeechRate.FAST, json.decodeFromString(SettingsDto.serializer(), json.encodeToString(SettingsDto.serializer(), withRate)).toDomain()?.speechRate)

        val beforeDay2 = """{"workshopName":"","workshopPhone":"","exportFormat":"png","audioEnabled":true,
            "speakItemTotals":true,"speakInvoiceTotals":true,"thankYouMessage":"","nextInvoiceNumber":3}"""
        assertEquals(SpeechRate.NORMAL, json.decodeFromString(SettingsDto.serializer(), beforeDay2).toDomain()?.speechRate)
        assertEquals(SpeechRate.NORMAL, withRate.copy(speechRate = "very-fast").toDomain()?.speechRate)
    }
}
