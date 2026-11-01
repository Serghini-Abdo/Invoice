package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.backup.toDomain
import ma.workshop.invoice.domain.backup.toDto
import ma.workshop.invoice.domain.backup.BackupCodec
import ma.workshop.invoice.domain.backup.BackupParseResult
import ma.workshop.invoice.domain.backup.InvoiceRecord
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.flow.*
import ma.workshop.invoice.domain.format.InvoiceDocumentBuilder
import ma.workshop.invoice.domain.format.ItemDescriber
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.invoice.ProductRef
import ma.workshop.invoice.domain.pricing.*
import ma.workshop.invoice.domain.speech.DarijaSpeech
import ma.workshop.invoice.domain.speech.StandardSpeech
import kotlin.test.*

class VariationEntryTest {
    private var nextId = 0
    private val flow = InvoiceFlow { "line-${++nextId}" }
    private var state = flow.startNew()
    private val door = ProductRef(1, "Door", "door", PricingFormula.AREA, LengthUnit.CENTIMETER)
    private fun send(event: FlowEvent) { state = flow.reduce(state, event).state }
    private fun start(product: ProductRef = door) {
        send(FlowEvent.CustomerContinue)
        send(FlowEvent.AddProductPressed)
        send(FlowEvent.ProductChosen(product))
    }
    private fun fill(index: Int, width: String, rate: String = "500") {
        send(FlowEvent.FormEdited(state.pending!!.forms[index].copy(widthText = width, heightText = "200", rateText = rate), index))
    }
    private fun confirm() { send(FlowEvent.EntrySubmitted); send(FlowEvent.ItemConfirmed) }

    @Test fun `four sizes commit together with independent ids totals and edits`() {
        start()
        for (index in 0..3) {
            fill(index, (90 + index * 10).toString())
            if (index < 3) send(FlowEvent.AddVariation)
        }
        assertTrue(state.items.isEmpty())
        confirm()
        assertEquals(FlowStep.ItemList, state.step)
        assertEquals(4, state.items.map { it.id }.toSet().size)
        assertEquals(listOf(90000L, 100000L, 110000L, 120000L), state.items.map { it.pricing.total.centimes })
        val original = state.items
        send(FlowEvent.EditItemPressed(original[1].id))
        send(FlowEvent.FormEdited(state.pending!!.form.copy(rateText = "600")))
        confirm()
        assertEquals(original.filterIndexed { index, _ -> index != 1 }, state.items.filterIndexed { index, _ -> index != 1 })
        assertEquals(120000L, state.items[1].pricing.total.centimes)
        send(FlowEvent.ItemConfirmed)
        assertEquals(4, state.items.size)
    }

    @Test fun `copied rates stay pending through size edits and are accepted by add or confirm`() {
        start(); fill(0, "90"); send(FlowEvent.AddVariation)
        val next = state.pending!!.forms[1]
        assertEquals("500", next.rateText)
        assertEquals("", next.widthText)
        assertEquals(1, next.quantity)
        assertTrue(next.rateNeedsConfirmation)
        fill(1, "100")
        assertTrue(state.pending!!.forms[1].rateNeedsConfirmation)
        send(FlowEvent.AddVariation)
        assertFalse(state.pending!!.forms[1].rateNeedsConfirmation)
        assertTrue(state.pending!!.forms[2].rateNeedsConfirmation)
        fill(2, "110", "650")
        assertFalse(state.pending!!.forms[2].rateNeedsConfirmation)
        send(FlowEvent.EntrySubmitted)
        assertTrue(state.pending!!.forms.none { it.rateNeedsConfirmation })
        send(FlowEvent.ItemConfirmed)
        assertEquals(3, state.items.size)
    }

    @Test fun `invalid added line prevents partial commit and can be removed`() {
        start(); fill(0, "90"); send(FlowEvent.AddVariation)
        confirm()
        assertEquals(FlowStep.ItemEntry, state.step)
        assertTrue(state.pending!!.showErrors)
        assertTrue(state.items.isEmpty())
        send(FlowEvent.AddVariation)
        assertEquals(2, state.pending!!.forms.size)
        send(FlowEvent.RemoveVariation(1))
        confirm()
        assertEquals(1, state.items.size)
    }

    @Test fun `draft restores every size and the unacknowledged copied price`() {
        start(); fill(0, "90"); send(FlowEvent.AddVariation)
        send(FlowEvent.FormEdited(state.pending!!.forms[1].copy(widthText = "100"), 1))
        val restored = assertNotNull(DraftCodec.decode(assertNotNull(DraftCodec.encode(state))))
        assertEquals(state.pending, restored.pending)
        assertTrue(restored.pending!!.forms[1].rateNeedsConfirmation)
    }

    @Test fun `width and height rates convert centimetres and round once after quantity`() {
        for (method in listOf(PricingFormula.WIDTH, PricingFormula.HEIGHT)) {
            val form = ItemEntryForm(method, widthText = "12.345", heightText = "12.345", widthUnit = LengthUnit.CENTIMETER,
                heightUnit = LengthUnit.CENTIMETER, quantity = 3, rateText = "80")
            val pricing = assertNotNull(form.evaluate().pricing)
            assertEquals(Fixtures.dh("29.63"), pricing.total)
            assertEquals(method.measurements.width, pricing.width != null)
            assertEquals(method.measurements.height, pricing.height != null)
            assertNotNull(ItemDescriber.measures(pricing))
            assertFalse(DarijaSpeech.calculation(pricing).contains("المحيط"))
            assertTrue(StandardSpeech.calculation(pricing).contains("حسب"))
            assertNull(form.copy(widthText = "", heightText = "").evaluate().pricing)
        }
    }

    @Test fun `per piece measurements are optional configuration and survive snapshots`() {
        for (measurements in Measurements.entries) {
            val product = Fixtures.backupData().products.first().copy(formula = PricingFormula.PER_ITEM, measurements = measurements)
            assertEquals(product, product.toDto().toDomain())
            val form = ItemEntryForm.forProduct(product.formula, LengthUnit.METER, product.measurements)
                .copy(widthText = "1.2", heightText = "2.1", rateText = "800", quantity = 2)
            val pricing = assertNotNull(form.evaluate().pricing)
            assertEquals(Fixtures.dh("1600"), pricing.total)
            assertEquals(measurements.width, pricing.width != null)
            assertEquals(measurements.height, pricing.height != null)
            val item = InvoiceItem("item", product.id, product.name, product.illustration, pricing)
            assertEquals(item, item.toDto().toDomain())
            assertEquals(pricing, assertNotNull(ItemEntryForm.fromPricing(pricing).evaluate().pricing))
            assertEquals(pricing, assertIs<PricingResult.Success>(PricingEngine.recompute(pricing)).pricing)
        }
        val oldProduct = Fixtures.backupData().products.first().toDto().copy(measurements = null)
        assertEquals(Measurements.BOTH, oldProduct.toDomain()!!.measurements)
    }

    @Test fun `custom labour is a named fixed amount without catalogue quantity or dimensions`() {
        send(FlowEvent.CustomerContinue)
        send(FlowEvent.CustomChargePressed)
        send(FlowEvent.FormEdited(state.pending!!.form.copy(rateText = "350")))
        confirm()
        assertTrue(state.items.isEmpty())
        send(FlowEvent.CustomChargeEdited(" Installation labour "))
        send(FlowEvent.AddVariation)
        assertEquals(1, state.pending!!.forms.size)
        confirm()
        val item = state.items.single()
        assertNull(item.productId)
        assertEquals("Installation labour", item.productName)
        assertEquals(Fixtures.dh("350"), item.pricing.total)
        val row = InvoiceDocumentBuilder.row(1, item)
        assertNull(row.quantity)
        assertNull(row.measures)
        assertFalse(row.formula.contains("قطعة"))
        assertFalse(DarijaSpeech.itemLine(item.productName, item.pricing).contains("العدد"))
        assertFalse(StandardSpeech.itemLine(item.productName, item.pricing).contains("العدد"))
        assertIs<PricingResult.Failure>(PricingEngine.recompute(item.pricing.copy(quantity = 2)))
        assertIs<PricingResult.Failure>(PricingEngine.recompute(item.pricing.copy(width = Fixtures.m("1"))))
        assertEquals(item, item.toDto().toDomain())
        send(FlowEvent.EditItemPressed(item.id))
        send(FlowEvent.CustomChargeEdited("Labour"))
        confirm()
        assertEquals("Labour", state.items.single().productName)
    }

    @Test fun `new pricing methods and measured pieces survive validated backups`() {
        val items = PricingFormula.entries.mapIndexed { index, method ->
            val form = ItemEntryForm(method, widthText = "1.2", heightText = "2.1", rateText = "80",
                measurements = if (method == PricingFormula.PER_ITEM) Measurements.BOTH else method.measurements)
            InvoiceItem("line-$index", null, method.id, "other", assertNotNull(form.evaluate().pricing))
        }
        val invoice = Fixtures.invoice().copy(items = items, deposit = Money.ZERO)
        val data = Fixtures.backupData().copy(invoices = listOf(InvoiceRecord(invoice, emptyList())))
        val document = BackupCodec.toDocument(data, 1, "test")
        val parsed = assertIs<BackupParseResult.Valid>(BackupCodec.parse(BackupCodec.encode(document)))
        assertEquals(data, parsed.data)
        val tampered = document.copy(invoices = document.invoices.map { it.copy(invoice = it.invoice.copy(
            items = it.invoice.items.map { item -> if (item.formula == "fixed") item.copy(quantity = 2) else item },
        )) })
        assertIs<BackupParseResult.Invalid>(BackupCodec.parse(BackupCodec.encode(tampered)))
    }
}
