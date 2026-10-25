package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.format.ArabicNumberWords
import ma.workshop.invoice.domain.format.InvoiceDocumentBuilder
import ma.workshop.invoice.domain.format.ItemDescriber
import ma.workshop.invoice.domain.format.SpokenText
import ma.workshop.invoice.domain.invoice.HistoryFilter
import ma.workshop.invoice.domain.invoice.HistoryQuery
import ma.workshop.invoice.domain.invoice.InvoiceSummary
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.PricingFormula
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatAndDocumentTest {

    private val nb = ArabicFormat.NBSP
    private val rlm = ArabicFormat.RLM
    private val zone = ZoneId.of("Africa/Casablanca")

    @Test
    fun `amounts use a no-break space for thousands and trim zero centimes`() {
        assertEquals("1${nb}260${nb}DH", ArabicFormat.amount(Money(126_000)))
        assertEquals("0${nb}DH", ArabicFormat.amount(Money.ZERO))
        assertEquals("1${nb}260,50${nb}DH", ArabicFormat.amount(Money(126_050)))
        assertEquals("0,05${nb}DH", ArabicFormat.amount(Money(5)))
        assertEquals("1${nb}234${nb}567${nb}DH", ArabicFormat.amount(Money(123_456_700)))
        assertEquals("999${nb}DH", ArabicFormat.amount(Money(99_900)))
    }

    @Test
    fun `lengths show two decimals in metres and only needed decimals in centimetres`() {
        assertEquals("1,20", ArabicFormat.length(Fixtures.m("1.2")))
        assertEquals("2,105", ArabicFormat.length(Fixtures.m("2.105")))
        assertEquals("120", ArabicFormat.length(Fixtures.cm("120")))
        assertEquals("120,5", ArabicFormat.length(Fixtures.cm("120.5")))
    }

    @Test
    fun `decimal formatting is display only and trims`() {
        assertEquals("2,52", ArabicFormat.decimal(BigDecimal("2.52"), minDecimals = 2))
        assertEquals("7", ArabicFormat.decimal(BigDecimal("7")))
        assertEquals("7,00", ArabicFormat.decimal(BigDecimal("7"), minDecimals = 2))
        assertEquals("0,1109", ArabicFormat.decimal(BigDecimal("0.110889")))
    }

    @Test
    fun `dates and numbers`() {
        // 2026-10-25T12:00:00Z
        assertEquals("25/10/2026", ArabicFormat.date(1_792_929_600_000, zone))
        assertEquals("2026-10-25", ArabicFormat.fileDate(1_792_929_600_000, zone))
        assertEquals("05/03/2026", ArabicFormat.date(LocalDate.of(2026, 3, 5)))
        assertEquals("0007", ArabicFormat.invoiceNumber(7))
        assertEquals("12345", ArabicFormat.invoiceNumber(12345))
    }

    @Test
    fun `item description for the spec example door`() {
        val pricing = Fixtures.pricing(PricingFormula.AREA, Fixtures.m("1.20"), Fixtures.m("2.10"), 1, "500")
        assertEquals("1,20 × 2,10 م", ItemDescriber.measures(pricing))
        assertEquals("500${nb}DH / م²", ItemDescriber.rate(pricing))
        assertEquals("2,52 م²", ItemDescriber.baseAmount(pricing))
        assertEquals("2,52 م² × 500${nb}DH$rlm × 1", ItemDescriber.calculation(pricing))
        val lines = ItemDescriber.breakdown(pricing)
        assertEquals("1,20 × 2,10 = 2,52 م²", lines.first { it.label == "المساحة" }.value)
        assertEquals("2,52 م² × 500${nb}DH$rlm × 1 = 1${nb}260${nb}DH", lines.last().value)
    }

    @Test
    fun `centimetre input is shown in its own unit but the formula line uses metres`() {
        val pricing = Fixtures.pricing(PricingFormula.AREA, Fixtures.cm("120"), Fixtures.cm("210"), 2, "500")
        assertEquals("120 × 210 سم", ItemDescriber.measures(pricing))
        val lines = ItemDescriber.breakdown(pricing)
        assertEquals("120 سم", lines.first { it.label == "العرض" }.value)
        assertEquals("1,20 × 2,10 = 2,52 م²", lines.first { it.label == "المساحة" }.value)
        assertEquals("2${nb}520${nb}DH", ArabicFormat.amount(pricing.total))
    }

    @Test
    fun `mixed units are labelled per dimension`() {
        val pricing = Fixtures.pricing(PricingFormula.AREA, Fixtures.cm("100"), Fixtures.m("2"), 1, "600")
        assertEquals("100 سم × 2,00 م", ItemDescriber.measures(pricing))
    }

    @Test
    fun `perimeter and per item descriptions`() {
        val frame = Fixtures.pricing(PricingFormula.PERIMETER, Fixtures.m("1.5"), Fixtures.m("2"), 3, "80")
        assertEquals("80${nb}DH / م", ItemDescriber.rate(frame))
        assertEquals("7 م", ItemDescriber.baseAmount(frame))
        assertEquals("2 × (1,50 + 2,00) = 7 م", ItemDescriber.breakdown(frame).first { it.label == "المحيط" }.value)

        val hinges = Fixtures.pricing(PricingFormula.PER_ITEM, quantity = 4, rate = "30")
        assertNull(ItemDescriber.measures(hinges))
        assertNull(ItemDescriber.baseAmount(hinges))
        assertEquals("30${nb}DH / قطعة", ItemDescriber.rate(hinges))
        assertEquals("4 × 30${nb}DH", ItemDescriber.calculation(hinges))
        assertTrue(ItemDescriber.breakdown(hinges).none { it.label == "العرض" })
    }

    // Scenario 16 (content half): the document carries correct Arabic text, measurements, totals, balance.
    @Test
    fun `invoice document carries every required field`() {
        val invoice = Fixtures.invoice(now = 1_792_929_600_000)
        val doc = InvoiceDocumentBuilder.build(invoice, Fixtures.settings(), zone)

        assertEquals("فاتورة", doc.title)
        assertEquals("ورشة الأمل للحدادة", doc.workshopName)
        assertEquals("0522000000", doc.workshopPhone)
        assertEquals("0001", doc.number)
        assertEquals("25/10/2026", doc.date)
        assertEquals("أحمد", doc.customerName)
        assertEquals("0612345678", doc.customerPhone)
        assertNull(doc.updatedNote)

        assertEquals(2, doc.rows.size)
        val door = doc.rows[0]
        assertEquals(1, door.position)
        assertEquals("door", door.illustration)
        assertEquals("باب حديد", door.name)
        assertEquals("1,20 × 2,10 م", door.measures)
        assertEquals(1, door.quantity)
        assertEquals("500${nb}DH / م²", door.rate)
        assertEquals("1${nb}260${nb}DH", door.total)
        val hinges = doc.rows[1]
        assertNull(hinges.measures)
        assertEquals(4, hinges.quantity)
        assertEquals("120${nb}DH", hinges.total)

        assertEquals("1${nb}380${nb}DH", doc.total)
        assertEquals("500${nb}DH", doc.deposit)
        assertEquals("880${nb}DH", doc.remaining)
        assertFalse(doc.isFullyPaid)
        assertEquals("شكراً لثقتكم", doc.thankYou)
    }

    @Test
    fun `optional customer and thank you are omitted when blank and paid invoices are flagged`() {
        val invoice = Fixtures.invoice(customer = ma.workshop.invoice.domain.invoice.CustomerInfo(), deposit = Fixtures.dh("1380"))
        val doc = InvoiceDocumentBuilder.build(invoice, Fixtures.settings().copy(thankYouMessage = "  "), zone)
        assertNull(doc.customerName)
        assertNull(doc.customerPhone)
        assertNull(doc.thankYou)
        assertTrue(doc.isFullyPaid)
        assertEquals("0${nb}DH", doc.remaining)
    }

    @Test
    fun `an edited invoice is clearly marked`() {
        val edited = Fixtures.backupData().invoices[1].invoice
        val doc = InvoiceDocumentBuilder.build(edited, Fixtures.settings(), zone)
        assertTrue(doc.updatedNote!!.startsWith("فاتورة معدلة"))
    }

    @Test
    fun `file names are ASCII and descriptive`() {
        val invoice = Fixtures.invoice(number = 7, now = 1_792_929_600_000)
        assertEquals("invoice-0007-2026-10-25.pdf", InvoiceDocumentBuilder.fileName(invoice, "pdf", zone))
        assertEquals("invoice-0007-2026-10-25.png", InvoiceDocumentBuilder.fileName(invoice, "png", zone))
    }

    // ---- spoken text ----

    @Test
    fun `arabic number words`() {
        val cases = mapOf(
            0L to "صفر",
            1L to "واحد",
            11L to "أحد عشر",
            21L to "واحد وعشرون",
            100L to "مائة",
            260L to "مائتان وستون",
            1_000L to "ألف",
            2_000L to "ألفان",
            5_000L to "خمسة آلاف",
            11_000L to "أحد عشر ألف",
            1_260L to "ألف و" + "مائتان وستون",
            2_520L to "ألفان و" + "خمسمائة وعشرون",
            1_000_000L to "مليون",
            2_500_000L to "مليونان و" + "خمسمائة ألف",
        )
        for ((n, words) in cases) assertEquals(words, ArabicNumberWords.integer(n), "number $n")
    }

    @Test
    fun `spoken amounts`() {
        assertEquals("ألف ومائتان وستون درهم", SpokenText.amount(Money(126_000)))
        assertEquals("درهم واحد", SpokenText.amount(Money(100)))
        assertEquals("درهمان", SpokenText.amount(Money(200)))
        assertEquals("خمسة دراهم", SpokenText.amount(Money(500)))
        assertEquals("خمسة دراهم وخمسون سنتيم", SpokenText.amount(Money(550)))
        assertEquals("خمسة وعشرون سنتيم", SpokenText.amount(Money(25)))
        assertEquals("صفر درهم", SpokenText.amount(Money.ZERO))
    }

    @Test
    fun `spoken invoice summary covers total deposit and remaining`() {
        val summary = ma.workshop.invoice.domain.invoice.PaymentCalculator.summarize(Money(138_000), Money(50_000))!!
        val text = SpokenText.invoiceSummary(summary)
        assertTrue(text.contains("ألف وثلاثمائة وثمانون درهم"), text)
        assertTrue(text.contains("التسبيق"), text)
        assertTrue(text.contains("الباقي ثمانمائة وثمانون درهم"), text)

        val paid = ma.workshop.invoice.domain.invoice.PaymentCalculator.summarize(Money(10_000), Money(10_000))!!
        assertTrue(SpokenText.invoiceSummary(paid).contains("خالص"))
        assertFalse(SpokenText.invoiceSummary(paid).contains("الباقي"))
    }

    // ---- history search ----

    private fun summary(number: Long, createdAt: Long, name: String, phone: String = "") = InvoiceSummary(
        id = "id$number", number = number, createdAt = createdAt, updatedAt = createdAt, revision = 1,
        customerName = name, customerPhone = phone, itemCount = 1,
        total = Money(100), deposit = Money.ZERO, remaining = Money(100),
    )

    private fun millis(date: String, hour: Int = 12) =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val sample = listOf(
        summary(1, millis("2026-10-01"), "أحمد العلوي", "0612345678"),
        summary(2, millis("2026-10-10"), "سعيد", ""),
        summary(3, millis("2026-10-10", hour = 18), "إبراهيم", "0699000000"),
        summary(4, millis("2026-10-25", hour = 23), "", ""),
    )

    @Test
    fun `history is newest first`() {
        assertEquals(listOf(4L, 3L, 2L, 1L), HistoryFilter.apply(sample, HistoryQuery(), zone).map { it.number })
    }

    @Test
    fun `search by customer name ignores diacritics and alef variants`() {
        assertEquals(listOf(3L), HistoryFilter.apply(sample, HistoryQuery(text = "ابراهيم"), zone).map { it.number })
        assertEquals(listOf(3L), HistoryFilter.apply(sample, HistoryQuery(text = "إبراهيم"), zone).map { it.number })
        assertEquals(listOf(1L), HistoryFilter.apply(sample, HistoryQuery(text = "احمد"), zone).map { it.number })
        assertEquals(listOf(1L), HistoryFilter.apply(sample, HistoryQuery(text = "العلوي"), zone).map { it.number })
        assertEquals(listOf(2L), HistoryFilter.apply(sample, HistoryQuery(text = "سَعيد"), zone).map { it.number })
    }

    @Test
    fun `search also finds phone numbers and invoice numbers including Arabic digits`() {
        assertEquals(listOf(3L), HistoryFilter.apply(sample, HistoryQuery(text = "0699"), zone).map { it.number })
        assertEquals(listOf(3L), HistoryFilter.apply(sample, HistoryQuery(text = "٠٦٩٩"), zone).map { it.number })
        assertEquals(listOf(2L), HistoryFilter.apply(sample, HistoryQuery(text = "0002"), zone).map { it.number })
    }

    @Test
    fun `date range is inclusive of both days in local time`() {
        val q = HistoryQuery(fromDate = LocalDate.parse("2026-10-10"), toDate = LocalDate.parse("2026-10-10"))
        assertEquals(listOf(3L, 2L), HistoryFilter.apply(sample, q, zone).map { it.number })

        val late = HistoryQuery(fromDate = LocalDate.parse("2026-10-25"))
        assertEquals(listOf(4L), HistoryFilter.apply(sample, late, zone).map { it.number })

        val early = HistoryQuery(toDate = LocalDate.parse("2026-10-01"))
        assertEquals(listOf(1L), HistoryFilter.apply(sample, early, zone).map { it.number })
    }

    @Test
    fun `name and date filters combine`() {
        val q = HistoryQuery(text = "سعيد", fromDate = LocalDate.parse("2026-10-11"))
        assertTrue(HistoryFilter.apply(sample, q, zone).isEmpty())
    }
}
