package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.flow.DraftCodec
import ma.workshop.invoice.domain.flow.FlowEvent
import ma.workshop.invoice.domain.flow.FlowMode
import ma.workshop.invoice.domain.flow.FlowStep
import ma.workshop.invoice.domain.flow.InvoiceFlow
import ma.workshop.invoice.domain.invoice.Change
import ma.workshop.invoice.domain.invoice.ChangeType
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.InvoiceDraft
import ma.workshop.invoice.domain.invoice.InvoiceVersioning
import ma.workshop.invoice.domain.invoice.ProductRef
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersioningAndDraftTest {

    private val original = Fixtures.invoice() // 1260 + 120 = 1380 DH, deposit 500

    @Test
    fun `a new invoice starts at revision 1 with computed totals`() {
        assertEquals(1, original.revision)
        assertEquals(false, original.isEdited)
        assertEquals(Fixtures.dh("1380"), original.total)
        assertEquals(Fixtures.dh("880"), original.remaining)
        assertEquals(original.createdAt, original.updatedAt)
    }

    @Test
    fun `customer details are trimmed snapshots`() {
        val invoice = Fixtures.invoice(customer = CustomerInfo("  أحمد  ", " 0612 "))
        assertEquals(CustomerInfo("أحمد", "0612"), invoice.customer)
    }

    @Test
    fun `editing keeps id number and creation time and bumps the revision`() {
        val changedDoor = Fixtures.doorItem("i1", "1.20", "2.10", "500", qty = 2) // 2520
        val draft = InvoiceDraft("inv-1", original.customer, listOf(changedDoor, original.items[1]), Fixtures.dh("800"))
        val (updated, revision) = InvoiceVersioning.edit(original, draft, "rev-1", now = original.createdAt + 5_000)

        assertEquals(original.id, updated.id)
        assertEquals(original.number, updated.number)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(original.createdAt + 5_000, updated.updatedAt)
        assertEquals(2, updated.revision)
        assertTrue(updated.isEdited)

        // totals and balance are recalculated from the edited items
        assertEquals(Fixtures.dh("2640"), updated.total)
        assertEquals(Fixtures.dh("1840"), updated.remaining)

        assertEquals(2, revision.revision)
        assertEquals(Fixtures.dh("1380"), revision.previousTotal)
        assertEquals(Fixtures.dh("2640"), revision.newTotal)
        assertEquals(Fixtures.dh("500"), revision.previousDeposit)
        assertEquals(Fixtures.dh("800"), revision.newDeposit)
        assertEquals(original, revision.previous) // the old version is fully recoverable
        assertEquals(
            listOf(Change(ChangeType.ITEM_CHANGED, "باب حديد"), Change(ChangeType.DEPOSIT_CHANGED)),
            revision.changes,
        )
    }

    @Test
    fun `diff reports added removed and changed items and customer changes`() {
        val extra = Fixtures.hingesItem("i9", 1, "10")
        val draft = InvoiceDraft("inv-1", CustomerInfo("سعيد", "0612345678"), listOf(original.items[0], extra), original.deposit)
        val (_, revision) = InvoiceVersioning.edit(original, draft, "r", now = 2)
        assertEquals(
            listOf(
                Change(ChangeType.ITEM_ADDED, "مفصلات"),
                Change(ChangeType.ITEM_REMOVED, "مفصلات"),
                Change(ChangeType.CUSTOMER_CHANGED),
            ),
            revision.changes,
        )
    }

    // Scenario 14 (domain part): an item's snapshot is independent from the catalog definition.
    @Test
    fun `changing a catalog definition cannot change a saved item`() {
        var catalogDoor = Fixtures.door
        val saved = Fixtures.invoice(items = listOf(Fixtures.doorItem("i1", "1.20", "2.10", "500")))
        val before = saved.copy()

        catalogDoor = catalogDoor.copy(
            name = "باب حديد مزخرف",
            illustration = "gate",
            formula = PricingFormula.PERIMETER,
            isActive = false,
        )

        assertEquals(before, saved)
        assertEquals("باب حديد", saved.items[0].productName)
        assertEquals("door", saved.items[0].illustration)
        assertEquals(PricingFormula.AREA, saved.items[0].pricing.formula)
        assertEquals(Fixtures.dh("1260"), saved.total)
        assertEquals(false, catalogDoor.isActive)
    }

    // ---- drafts ----

    private val flow = InvoiceFlow { "id-${System.nanoTime()}" }
    private val door = ProductRef(1, "باب حديد", "door", PricingFormula.AREA, LengthUnit.METER)

    private fun withItem(): ma.workshop.invoice.domain.flow.FlowState {
        var state = flow.startNew()
        fun send(e: FlowEvent) { state = flow.reduce(state, e).state }
        send(FlowEvent.CustomerEdited("أحمد", "0612"))
        send(FlowEvent.CustomerContinue)
        send(FlowEvent.AddProductPressed)
        send(FlowEvent.ProductChosen(door))
        send(FlowEvent.FormEdited(state.pending!!.form.copy(widthText = "1.2", heightText = "2.1", rateText = "500", quantity = 2)))
        send(FlowEvent.EntrySubmitted)
        send(FlowEvent.ItemConfirmed)
        send(FlowEvent.ContinueToReview)
        send(FlowEvent.DepositEdited("300"))
        return state
    }

    @Test
    fun `an untouched new invoice is not saved as a draft`() {
        assertNull(DraftCodec.encode(flow.startNew()))
    }

    @Test
    fun `a draft restores customer items and deposit on the item list`() {
        val state = withItem()
        val text = assertNotNull(DraftCodec.encode(state))
        val restored = assertNotNull(DraftCodec.decode(text))
        assertEquals(FlowStep.ItemList, restored.step) // review is not restored, the list is the safe place
        assertEquals(state.customer, restored.customer)
        assertEquals(state.items, restored.items)
        assertEquals("300", restored.depositText)
        assertEquals(FlowMode.New, restored.mode)
        assertEquals(Fixtures.dh("2520"), restored.total)
    }

    @Test
    fun `a half entered item is restored on the details screen with its typed values`() {
        var state = flow.startNew()
        fun send(e: FlowEvent) { state = flow.reduce(state, e).state }
        send(FlowEvent.CustomerContinue)
        send(FlowEvent.AddProductPressed)
        send(FlowEvent.ProductChosen(door))
        send(FlowEvent.FormEdited(state.pending!!.form.copy(widthText = "1.2", quantity = 3)))

        val restored = assertNotNull(DraftCodec.decode(assertNotNull(DraftCodec.encode(state))))
        assertEquals(FlowStep.ItemEntry, restored.step)
        assertEquals("1.2", restored.pending!!.form.widthText)
        assertEquals(3, restored.pending!!.form.quantity)
        assertEquals(door, restored.pending!!.product)
    }

    @Test
    fun `editing an existing invoice is never saved as a draft`() {
        assertNull(DraftCodec.encode(flow.startEdit(original).copy(depositText = "10")))
    }

    @Test
    fun `an unreadable draft is ignored instead of crashing`() {
        assertNull(DraftCodec.decode("not json"))
        assertNull(DraftCodec.decode("{}"))
    }
}
