package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.flow.FlowEffect
import ma.workshop.invoice.domain.flow.FlowEvent
import ma.workshop.invoice.domain.flow.FlowIssue
import ma.workshop.invoice.domain.flow.FlowMode
import ma.workshop.invoice.domain.flow.FlowState
import ma.workshop.invoice.domain.flow.FlowStep
import ma.workshop.invoice.domain.flow.InvoiceFlow
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.ProductRef
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemEntryForm
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceFlowTest {

    private var counter = 0
    private val flow = InvoiceFlow { "item-${++counter}" }

    private val door = ProductRef(1, "باب حديد", "door", PricingFormula.AREA, LengthUnit.METER)
    private val hinges = ProductRef(5, "مفصلات", "hinges", PricingFormula.PER_ITEM, LengthUnit.METER)
    private val frame = ProductRef(4, "إطار حديد", "frame", PricingFormula.PERIMETER, LengthUnit.CENTIMETER)

    /** Applies events in order, collecting the final state and every effect. */
    private class Run(var state: FlowState, val effects: MutableList<FlowEffect> = mutableListOf())

    private fun Run.send(event: FlowEvent): Run {
        val t = flow.reduce(state, event)
        state = t.state
        t.effect?.let { effects += it }
        return this
    }

    private fun newRun(): Run = Run(flow.startNew())

    private fun Run.toItemList(): Run = send(FlowEvent.CustomerContinue)

    private fun Run.addItem(product: ProductRef, form: (ItemEntryForm) -> ItemEntryForm): Run {
        send(FlowEvent.AddProductPressed)
        send(FlowEvent.ProductChosen(product))
        val current = state.pending!!.form
        send(FlowEvent.FormEdited(form(current)))
        send(FlowEvent.EntrySubmitted)
        return send(FlowEvent.ItemConfirmed)
    }

    private fun doorForm(width: String, height: String, rate: String, qty: Int = 1) = { f: ItemEntryForm ->
        f.copy(widthText = width, heightText = height, rateText = rate, quantity = qty)
    }

    @Test
    fun `a new invoice starts at the optional customer step`() {
        val run = newRun()
        assertEquals(FlowStep.Customer, run.state.step)
        assertTrue(run.state.customer.isBlank)
    }

    @Test
    fun `customer details are optional and the user can continue immediately`() {
        val run = newRun().toItemList()
        assertEquals(FlowStep.ItemList, run.state.step)
        assertEquals(CustomerInfo("", ""), run.state.customer)
    }

    // Scenario 11
    @Test
    fun `the plus button opens the catalog and more products can always be added`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.AddProductPressed)
        assertEquals(FlowStep.Catalog, run.state.step)

        run.send(FlowEvent.ProductChosen(door))
        assertEquals(FlowStep.ItemEntry, run.state.step)
        assertEquals(door, run.state.pending!!.product)
    }

    // Scenario 12
    @Test
    fun `every new item starts with quantity 1`() {
        val run = newRun().toItemList()
        for (product in listOf(door, hinges, frame)) {
            run.send(FlowEvent.AddProductPressed).send(FlowEvent.ProductChosen(product))
            assertEquals(1, run.state.pending!!.form.quantity)
            run.send(FlowEvent.ItemCancelled)
        }
    }

    // Scenario 10
    @Test
    fun `confirming an item returns to the item list not the catalog or payment`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.AddProductPressed).send(FlowEvent.ProductChosen(door))
        run.send(FlowEvent.FormEdited(doorForm("1.20", "2.10", "500")(run.state.pending!!.form)))
        run.send(FlowEvent.EntrySubmitted)
        assertEquals(FlowStep.ItemConfirm, run.state.step)
        assertEquals(Money(126_000), run.state.pending!!.confirmedPricing!!.total)

        run.send(FlowEvent.ItemConfirmed)
        assertEquals(FlowStep.ItemList, run.state.step)
        assertNull(run.state.pending)
        assertEquals(1, run.state.items.size)
        assertEquals(Money(126_000), run.state.total)
    }

    @Test
    fun `confirming after editing an existing item also returns to the item list`() {
        val run = newRun().toItemList().addItem(door, doorForm("1.2", "2.1", "500"))
        run.send(FlowEvent.EditItemPressed(run.state.items[0].id))
        assertEquals(FlowStep.ItemEntry, run.state.step)
        run.send(FlowEvent.EntrySubmitted).send(FlowEvent.ItemConfirmed)
        assertEquals(FlowStep.ItemList, run.state.step)
    }

    // Scenario 9
    @Test
    fun `the same product added twice creates two independent items`() {
        val run = newRun().toItemList()
            .addItem(door, doorForm("1.20", "2.10", "500"))
            .addItem(door, doorForm("1.00", "2.00", "600"))

        val items = run.state.items
        assertEquals(2, items.size)
        assertNotEquals(items[0].id, items[1].id)
        assertEquals(items[0].productId, items[1].productId)
        assertEquals(Money(126_000), items[0].pricing.total)
        assertEquals(Money(120_000), items[1].pricing.total)
        assertEquals(Money(500_00), items[0].pricing.unitRate)
        assertEquals(Money(600_00), items[1].pricing.unitRate)
        assertEquals(Money(246_000), run.state.total)
        assertEquals(FlowStep.ItemList, run.state.step)
    }

    @Test
    fun `identical repeated entries are still never merged`() {
        val run = newRun().toItemList()
            .addItem(door, doorForm("1", "2", "500"))
            .addItem(door, doorForm("1", "2", "500"))
        assertEquals(2, run.state.items.size)
        assertEquals(Money(200_000), run.state.total)
    }

    // Scenario 13
    @Test
    fun `editing one repeated item does not change the other`() {
        val run = newRun().toItemList()
            .addItem(door, doorForm("1.20", "2.10", "500"))
            .addItem(door, doorForm("1.00", "2.00", "600"))
        val (first, second) = run.state.items
        val secondBefore = second

        run.send(FlowEvent.EditItemPressed(first.id))
        val edited = run.state.pending!!.form.copy(quantity = 2, rateText = "550")
        run.send(FlowEvent.FormEdited(edited)).send(FlowEvent.EntrySubmitted).send(FlowEvent.ItemConfirmed)

        assertEquals(2, run.state.items.size)
        assertEquals(first.id, run.state.items[0].id) // same id, same position
        assertEquals(2, run.state.items[0].pricing.quantity)
        assertEquals(Money(277_200), run.state.items[0].pricing.total) // 2.52 * 550 * 2
        assertEquals(secondBefore, run.state.items[1]) // untouched, field for field
    }

    @Test
    fun `editing an item restores what was typed including its units`() {
        val run = newRun().toItemList().addItem(frame) { it.copy(widthText = "150", heightText = "200", rateText = "80", quantity = 3) }
        run.send(FlowEvent.EditItemPressed(run.state.items[0].id))
        val form = run.state.pending!!.form
        assertEquals("150", form.widthText)
        assertEquals(LengthUnit.CENTIMETER, form.widthUnit)
        assertEquals(3, form.quantity)
        assertEquals("80", form.rateText)
        assertEquals(frame.id, run.state.pending!!.product.id)
    }

    @Test
    fun `an invalid form stays on the details screen and flags errors`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.AddProductPressed).send(FlowEvent.ProductChosen(door))
        run.send(FlowEvent.EntrySubmitted)
        assertEquals(FlowStep.ItemEntry, run.state.step)
        assertTrue(run.state.pending!!.showErrors)
        assertNull(run.state.pending!!.confirmedPricing)
        assertTrue(run.state.items.isEmpty())
    }

    @Test
    fun `cancelling at the confirmation returns to the list without adding anything`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.AddProductPressed).send(FlowEvent.ProductChosen(hinges))
        run.send(FlowEvent.FormEdited(run.state.pending!!.form.copy(rateText = "30", quantity = 4)))
        run.send(FlowEvent.EntrySubmitted)
        assertEquals(FlowStep.ItemConfirm, run.state.step)
        run.send(FlowEvent.ItemCancelled)
        assertEquals(FlowStep.ItemList, run.state.step)
        assertTrue(run.state.items.isEmpty())
        assertNull(run.state.pending)
    }

    @Test
    fun `confirming without a priced pending item does nothing`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.ItemConfirmed)
        assertEquals(FlowStep.ItemList, run.state.step)
        assertTrue(run.state.items.isEmpty())
    }

    @Test
    fun `a double tap on confirm cannot add the item twice`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.AddProductPressed).send(FlowEvent.ProductChosen(hinges))
        run.send(FlowEvent.FormEdited(run.state.pending!!.form.copy(rateText = "30")))
        run.send(FlowEvent.EntrySubmitted).send(FlowEvent.ItemConfirmed).send(FlowEvent.ItemConfirmed)
        assertEquals(1, run.state.items.size)
    }

    @Test
    fun `removing an item needs confirmation`() {
        val run = newRun().toItemList()
            .addItem(door, doorForm("1", "2", "500"))
            .addItem(hinges) { it.copy(rateText = "30") }
        val first = run.state.items[0]

        run.send(FlowEvent.RemoveItemPressed(first.id))
        assertEquals(first.id, run.state.removalCandidate)
        assertEquals(2, run.state.items.size) // not removed yet

        run.send(FlowEvent.RemoveCancelled)
        assertEquals(2, run.state.items.size)
        assertNull(run.state.removalCandidate)

        run.send(FlowEvent.RemoveItemPressed(first.id)).send(FlowEvent.RemoveConfirmed)
        assertEquals(1, run.state.items.size)
        assertEquals("مفصلات", run.state.items[0].productName)
    }

    @Test
    fun `continue is blocked until there is at least one item`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.ContinueToReview)
        assertEquals(FlowStep.ItemList, run.state.step)
        assertEquals(FlowIssue.NO_ITEMS, run.state.issue)

        run.addItem(hinges) { it.copy(rateText = "30") }
        assertNull(run.state.issue)
        run.send(FlowEvent.ContinueToReview)
        assertEquals(FlowStep.Review, run.state.step)
    }

    // Scenarios 7, 8
    @Test
    fun `review defaults the deposit to zero and rejects a deposit above the total`() {
        val run = newRun().toItemList().addItem(door, doorForm("1.20", "2.10", "500")).send(FlowEvent.ContinueToReview)
        assertEquals("", run.state.depositText)
        assertEquals(Money.ZERO, run.state.deposit)

        run.send(FlowEvent.DepositEdited("1300")).send(FlowEvent.SavePressed)
        assertEquals(Problem.EXCEEDS_TOTAL, run.state.depositProblem)
        assertEquals(false, run.state.confirmingSave)
        assertTrue(run.effects.isEmpty())

        run.send(FlowEvent.DepositEdited("-5")).send(FlowEvent.SavePressed)
        assertEquals(Problem.NEGATIVE, run.state.depositProblem)
    }

    @Test
    fun `saving needs a final confirmation then emits exactly one save effect`() {
        val run = newRun().toItemList()
            .send(FlowEvent.CustomerEdited(" أحمد ", " 0612345678 "))
            .addItem(door, doorForm("1.20", "2.10", "500"))
            .send(FlowEvent.ContinueToReview)
            .send(FlowEvent.DepositEdited("500"))
            .send(FlowEvent.SavePressed)
        assertTrue(run.state.confirmingSave)
        assertTrue(run.effects.isEmpty())

        run.send(FlowEvent.SaveConfirmed).send(FlowEvent.SaveConfirmed)
        assertEquals(1, run.effects.size)
        val effect = assertIs<FlowEffect.Save>(run.effects.single())
        assertEquals(Money(50_000), effect.draft.deposit)
        assertNull(effect.draft.replacingInvoiceId)
        assertEquals(1, effect.draft.items.size)
        assertTrue(run.state.saving)
    }

    @Test
    fun `customer details entered at step one are carried into the draft`() {
        // The customer step is only editable while on it; go back to it from the list.
        val run = newRun()
            .send(FlowEvent.CustomerEdited(" أحمد ", " 0612345678 "))
            .toItemList()
            .addItem(hinges) { it.copy(rateText = "30") }
            .send(FlowEvent.ContinueToReview).send(FlowEvent.SavePressed).send(FlowEvent.SaveConfirmed)
        val effect = assertIs<FlowEffect.Save>(run.effects.single())
        assertEquals(CustomerInfo("أحمد", "0612345678"), effect.draft.customer)
    }

    // Scenario 18 (domain part)
    @Test
    fun `a failed save keeps every piece of data and allows retrying`() {
        val run = newRun().toItemList()
            .addItem(door, doorForm("1.20", "2.10", "500"))
            .send(FlowEvent.ContinueToReview).send(FlowEvent.DepositEdited("200"))
            .send(FlowEvent.SavePressed).send(FlowEvent.SaveConfirmed)
        val itemsBefore = run.state.items

        run.send(FlowEvent.SaveFailed)
        assertEquals(FlowStep.Review, run.state.step)
        assertEquals(FlowIssue.SAVE_FAILED, run.state.issue)
        assertEquals(itemsBefore, run.state.items)
        assertEquals("200", run.state.depositText)
        assertEquals(false, run.state.saving)

        run.send(FlowEvent.SavePressed).send(FlowEvent.SaveConfirmed)
        assertEquals(2, run.effects.count { it is FlowEffect.Save })
    }

    @Test
    fun `a successful save moves to the saved step`() {
        val run = newRun().toItemList().addItem(hinges) { it.copy(rateText = "30") }
            .send(FlowEvent.ContinueToReview).send(FlowEvent.SavePressed).send(FlowEvent.SaveConfirmed)
            .send(FlowEvent.SaveSucceeded("inv-1"))
        assertEquals(FlowStep.Saved("inv-1"), run.state.step)
        assertEquals(false, run.state.isDirty)
        run.send(FlowEvent.BackPressed)
        assertEquals(FlowEffect.Exit(keepDraft = false), run.effects.last())
    }

    @Test
    fun `back navigation walks the steps in reverse`() {
        val run = newRun().toItemList()
        run.send(FlowEvent.AddProductPressed)
        run.send(FlowEvent.BackPressed)
        assertEquals(FlowStep.ItemList, run.state.step)

        run.send(FlowEvent.AddProductPressed).send(FlowEvent.ProductChosen(hinges))
        run.send(FlowEvent.BackPressed)
        assertEquals(FlowStep.Catalog, run.state.step)

        run.send(FlowEvent.ProductChosen(hinges))
        run.send(FlowEvent.FormEdited(run.state.pending!!.form.copy(rateText = "30"))).send(FlowEvent.EntrySubmitted)
        run.send(FlowEvent.BackPressed)
        assertEquals(FlowStep.ItemEntry, run.state.step)
        assertEquals("30", run.state.pending!!.form.rateText) // typed values survive going back

        run.send(FlowEvent.BackPressed).send(FlowEvent.BackPressed)
        assertEquals(FlowStep.ItemList, run.state.step)
        run.send(FlowEvent.BackPressed)
        assertEquals(FlowStep.Customer, run.state.step)
    }

    @Test
    fun `back from review returns to the items so details can be corrected`() {
        val run = newRun().toItemList().addItem(hinges) { it.copy(rateText = "30") }.send(FlowEvent.ContinueToReview)
        run.send(FlowEvent.BackPressed)
        assertEquals(FlowStep.ItemList, run.state.step)
        run.send(FlowEvent.EditItemPressed(run.state.items[0].id))
        assertEquals(FlowStep.ItemEntry, run.state.step)
    }

    @Test
    fun `leaving an untouched invoice exits immediately`() {
        val run = newRun().send(FlowEvent.BackPressed)
        assertEquals(listOf<FlowEffect>(FlowEffect.Exit(keepDraft = false)), run.effects)
    }

    @Test
    fun `leaving with unsaved items asks first and never silently discards`() {
        val run = newRun().toItemList().addItem(hinges) { it.copy(rateText = "30") }
        run.send(FlowEvent.BackPressed) // list -> customer
        run.send(FlowEvent.BackPressed) // customer -> exit request
        assertTrue(run.state.confirmingExit)
        assertTrue(run.effects.isEmpty())
        assertEquals(1, run.state.items.size)

        run.send(FlowEvent.ExitCancelled)
        assertEquals(false, run.state.confirmingExit)
        assertEquals(1, run.state.items.size)

        run.send(FlowEvent.ExitPressed).send(FlowEvent.ExitKeepDraft)
        assertEquals(FlowEffect.Exit(keepDraft = true), run.effects.last())
    }

    @Test
    fun `discarding is explicit and asks the host to delete the draft`() {
        val run = newRun().toItemList().addItem(hinges) { it.copy(rateText = "30") }.send(FlowEvent.ExitPressed)
        run.send(FlowEvent.ExitDiscard)
        assertEquals(FlowEffect.Exit(keepDraft = false), run.effects.single())
    }

    @Test
    fun `back dismisses an open dialog before navigating`() {
        val run = newRun().toItemList().addItem(hinges) { it.copy(rateText = "30") }
        run.send(FlowEvent.RemoveItemPressed(run.state.items[0].id))
        run.send(FlowEvent.BackPressed)
        assertNull(run.state.removalCandidate)
        assertEquals(FlowStep.ItemList, run.state.step)
    }

    @Test
    fun `the user can return to the customer details from the item list`() {
        val run = newRun().toItemList().send(FlowEvent.EditCustomerPressed)
        assertEquals(FlowStep.Customer, run.state.step)
        run.send(FlowEvent.CustomerEdited("سعيد", "")).send(FlowEvent.CustomerContinue)
        assertEquals(FlowStep.ItemList, run.state.step)
        assertEquals("سعيد", run.state.customer.name)
    }

    @Test
    fun `events that do not belong to the current step are ignored`() {
        val run = newRun()
        run.send(FlowEvent.ItemConfirmed).send(FlowEvent.SaveConfirmed).send(FlowEvent.ContinueToReview)
            .send(FlowEvent.AddProductPressed).send(FlowEvent.ProductChosen(door))
        assertEquals(FlowStep.Customer, run.state.step)
        assertNull(run.state.pending)
    }

    // ---- editing an existing invoice ----

    private fun existingInvoice(): Invoice {
        val run = newRun().toItemList()
            .addItem(door, doorForm("1.20", "2.10", "500"))
            .addItem(hinges) { it.copy(rateText = "30", quantity = 4) }
        return Invoice(
            id = "inv-1", number = 7, createdAt = 1_000, updatedAt = 1_000, revision = 1,
            customer = CustomerInfo("أحمد", "0612345678"), items = run.state.items, deposit = Money(50_000),
        )
    }

    @Test
    fun `editing starts at the item list with the invoice's data`() {
        val invoice = existingInvoice()
        val state = flow.startEdit(invoice)
        assertEquals(FlowMode.Edit("inv-1"), state.mode)
        assertEquals(FlowStep.ItemList, state.step)
        assertEquals(invoice.items, state.items)
        assertEquals("500", state.depositText)
        assertEquals(false, state.isDirty)
    }

    @Test
    fun `editing an invoice and saving replaces the same invoice id and recalculates`() {
        val invoice = existingInvoice()
        val run = Run(flow.startEdit(invoice))
        assertEquals(Money(126_000 + 12_000), run.state.total)

        run.send(FlowEvent.EditItemPressed(invoice.items[1].id))
        run.send(FlowEvent.FormEdited(run.state.pending!!.form.copy(quantity = 10))).send(FlowEvent.EntrySubmitted).send(FlowEvent.ItemConfirmed)
        assertTrue(run.state.isDirty)
        assertEquals(Money(126_000 + 30_000), run.state.total)

        run.send(FlowEvent.ContinueToReview).send(FlowEvent.SavePressed).send(FlowEvent.SaveConfirmed)
        val effect = assertIs<FlowEffect.Save>(run.effects.single())
        assertEquals("inv-1", effect.draft.replacingInvoiceId)
        assertEquals(Money(50_000), effect.draft.deposit)
        assertEquals(Money(126_000 + 30_000), effect.draft.items.sumOf { it.pricing.total.centimes }.let(::Money))
    }

    @Test
    fun `editing the deposit alone makes an invoice dirty and shrinking the total below it is caught`() {
        val invoice = existingInvoice()
        val run = Run(flow.startEdit(invoice))
        // remove the door: total becomes 120 DH but the deposit is still 500 DH
        run.send(FlowEvent.RemoveItemPressed(invoice.items[0].id)).send(FlowEvent.RemoveConfirmed)
        run.send(FlowEvent.ContinueToReview).send(FlowEvent.SavePressed)
        assertEquals(Problem.EXCEEDS_TOTAL, run.state.depositProblem)
        run.send(FlowEvent.DepositEdited("100")).send(FlowEvent.SavePressed)
        assertTrue(run.state.confirmingSave)
    }

    @Test
    fun `leaving an edit with changes asks first and offers no draft`() {
        val invoice = existingInvoice()
        val run = Run(flow.startEdit(invoice))
        run.send(FlowEvent.BackPressed)
        assertEquals(FlowEffect.Exit(keepDraft = false), run.effects.last()) // nothing changed: leave at once

        val run2 = Run(flow.startEdit(invoice))
        run2.send(FlowEvent.RemoveItemPressed(invoice.items[0].id)).send(FlowEvent.RemoveConfirmed)
        run2.send(FlowEvent.BackPressed)
        assertTrue(run2.state.confirmingExit)
        run2.send(FlowEvent.ExitKeepDraft) // not offered for edits
        assertTrue(run2.state.confirmingExit)
        assertTrue(run2.effects.isEmpty())
        assertNotNull(run2.state.baseline)
    }
}
