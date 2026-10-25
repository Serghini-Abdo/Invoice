package ma.workshop.invoice.domain.flow

import ma.workshop.invoice.domain.input.MoneyParse
import ma.workshop.invoice.domain.input.MoneyParser
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.DepositParse
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceDraft
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.invoice.PaymentCalculator
import ma.workshop.invoice.domain.invoice.ProductRef
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemEntryForm
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.Problem

sealed interface FlowMode {
    data object New : FlowMode
    data class Edit(val invoiceId: String) : FlowMode
}

/** The screens of the invoice workflow, in the exact order the owner sees them. */
sealed interface FlowStep {
    /** Step 1: optional customer name and phone. */
    data object Customer : FlowStep

    /** Step 2: the items added so far, with the + button. Every item confirmation returns here. */
    data object ItemList : FlowStep

    /** The illustrated catalog, opened by the + button. */
    data object Catalog : FlowStep

    /** Step 3: dimensions, quantity and unit rate for the selected product. */
    data object ItemEntry : FlowStep

    /** Step 4: calculation breakdown, confirm or cancel. */
    data object ItemConfirm : FlowStep

    /** Step 5: totals, deposit, remaining balance and the final save confirmation. */
    data object Review : FlowStep

    /** Step 6: saved. Export and sharing happen from here and can never undo the save. */
    data class Saved(val invoiceId: String) : FlowStep
}

/** The item currently being entered (new) or edited (existing). */
data class PendingItem(
    val product: ProductRef,
    val form: ItemEntryForm,
    /** Non-null when editing an existing item; the item keeps its id and position. */
    val editingItemId: String? = null,
    /** Becomes true after the user tries to continue, so errors are not shown while typing. */
    val showErrors: Boolean = false,
    /** Set once the form is valid and the confirmation screen is showing. */
    val confirmedPricing: ItemPricing? = null,
)

enum class FlowIssue { NO_ITEMS, SAVE_FAILED }

/** What an edited invoice looked like when editing began, to tell whether anything changed. */
data class Baseline(val customer: CustomerInfo, val items: List<InvoiceItem>, val deposit: Money)

data class FlowState(
    val mode: FlowMode = FlowMode.New,
    val step: FlowStep = FlowStep.Customer,
    val customer: CustomerInfo = CustomerInfo(),
    val items: List<InvoiceItem> = emptyList(),
    val pending: PendingItem? = null,
    /** Item awaiting a "remove?" confirmation. */
    val removalCandidate: String? = null,
    val depositText: String = "",
    val depositProblem: Problem? = null,
    val issue: FlowIssue? = null,
    val confirmingSave: Boolean = false,
    val confirmingExit: Boolean = false,
    val saving: Boolean = false,
    val baseline: Baseline? = null,
) {
    val total: Money get() = PaymentCalculator.total(items.map { it.pricing })

    /** Deposit as currently typed, or null when the text is not a valid amount. */
    val deposit: Money?
        get() = when (val parsed = MoneyParser.parse(depositText)) {
            MoneyParse.Empty -> Money.ZERO
            is MoneyParse.Invalid -> null
            is MoneyParse.Value -> parsed.money
        }

    /** True when leaving now would lose something the user entered. */
    val isDirty: Boolean
        get() = when (step) {
            is FlowStep.Saved -> false
            else -> hasHalfTypedNewItem || if (baseline == null) {
                !customer.isBlank || items.isNotEmpty() || depositText.isNotBlank()
            } else {
                customer.normalized() != baseline.customer.normalized() ||
                    items != baseline.items ||
                    deposit != baseline.deposit
            }
        }

    /** A brand-new item whose details the user has already started typing but not yet confirmed. */
    private val hasHalfTypedNewItem: Boolean
        get() = pending != null &&
            pending.editingItemId == null &&
            pending.form != ItemEntryForm.forProduct(pending.product.formula, pending.product.defaultUnit)
}

sealed interface FlowEvent {
    data class CustomerEdited(val name: String, val phone: String) : FlowEvent
    data object CustomerContinue : FlowEvent
    data object EditCustomerPressed : FlowEvent

    /** The "+ إضافة منتوج" button. */
    data object AddProductPressed : FlowEvent
    data class ProductChosen(val product: ProductRef) : FlowEvent
    data class FormEdited(val form: ItemEntryForm) : FlowEvent
    data object EntrySubmitted : FlowEvent
    data object ItemConfirmed : FlowEvent
    data object ItemCancelled : FlowEvent

    data class EditItemPressed(val itemId: String) : FlowEvent
    data class RemoveItemPressed(val itemId: String) : FlowEvent
    data object RemoveConfirmed : FlowEvent
    data object RemoveCancelled : FlowEvent

    data object ContinueToReview : FlowEvent
    data object GoToItems : FlowEvent
    data class DepositEdited(val text: String) : FlowEvent
    data object SavePressed : FlowEvent
    data object SaveDismissed : FlowEvent
    data object SaveConfirmed : FlowEvent
    data class SaveSucceeded(val invoiceId: String) : FlowEvent
    data object SaveFailed : FlowEvent

    data object BackPressed : FlowEvent
    data object ExitPressed : FlowEvent
    data object ExitKeepDraft : FlowEvent
    data object ExitDiscard : FlowEvent
    data object ExitCancelled : FlowEvent
}

/** Side effects the host (ViewModel) must perform; the reducer itself stays pure. */
sealed interface FlowEffect {
    data class Save(val draft: InvoiceDraft) : FlowEffect

    /** Leave the workflow. [keepDraft] false means the auto-saved draft should be deleted. */
    data class Exit(val keepDraft: Boolean) : FlowEffect
}

data class Transition(val state: FlowState, val effect: FlowEffect? = null)

/**
 * The invoice creation/editing workflow as a pure state machine. The UI renders [FlowState.step]
 * and sends [FlowEvent]s; it never navigates on its own. That makes the workflow rules testable
 * without Android, in particular:
 *
 *  - confirming an item always lands on [FlowStep.ItemList];
 *  - the same product can be added many times and every addition is an independent item;
 *  - editing one item never touches another;
 *  - saving is a separate step whose failure leaves all data intact.
 *
 * [newId] supplies ids for new invoice items (injected so tests are deterministic).
 */
class InvoiceFlow(private val newId: () -> String) {

    fun startNew(): FlowState = FlowState()

    fun startEdit(invoice: Invoice): FlowState = FlowState(
        mode = FlowMode.Edit(invoice.id),
        step = FlowStep.ItemList,
        customer = invoice.customer,
        items = invoice.items,
        depositText = if (invoice.deposit.isZero) "" else MoneyParser.toEditableText(invoice.deposit),
        baseline = Baseline(invoice.customer, invoice.items, invoice.deposit),
    )

    fun reduce(state: FlowState, event: FlowEvent): Transition {
        val step = state.step
        return when (event) {
            is FlowEvent.CustomerEdited ->
                if (step == FlowStep.Customer) {
                    Transition(state.copy(customer = CustomerInfo(event.name, event.phone)))
                } else {
                    Transition(state)
                }

            FlowEvent.CustomerContinue ->
                if (step == FlowStep.Customer) Transition(state.copy(step = FlowStep.ItemList)) else Transition(state)

            FlowEvent.EditCustomerPressed ->
                if (step == FlowStep.ItemList) Transition(state.copy(step = FlowStep.Customer)) else Transition(state)

            FlowEvent.AddProductPressed ->
                if (step == FlowStep.ItemList) {
                    Transition(state.copy(step = FlowStep.Catalog, issue = null))
                } else {
                    Transition(state)
                }

            is FlowEvent.ProductChosen ->
                if (step == FlowStep.Catalog) {
                    Transition(
                        state.copy(
                            step = FlowStep.ItemEntry,
                            pending = PendingItem(
                                product = event.product,
                                form = ItemEntryForm.forProduct(event.product.formula, event.product.defaultUnit),
                            ),
                        ),
                    )
                } else {
                    Transition(state)
                }

            is FlowEvent.FormEdited -> {
                val pending = state.pending
                if (step == FlowStep.ItemEntry && pending != null && event.form.formula == pending.product.formula) {
                    Transition(state.copy(pending = pending.copy(form = event.form, confirmedPricing = null)))
                } else {
                    Transition(state)
                }
            }

            FlowEvent.EntrySubmitted -> submitEntry(state)

            FlowEvent.ItemConfirmed -> confirmItem(state)

            FlowEvent.ItemCancelled ->
                if (step == FlowStep.ItemEntry || step == FlowStep.ItemConfirm) {
                    Transition(state.copy(step = FlowStep.ItemList, pending = null))
                } else {
                    Transition(state)
                }

            is FlowEvent.EditItemPressed -> {
                val item = state.items.firstOrNull { it.id == event.itemId }
                if (step == FlowStep.ItemList && item != null) {
                    val product = ProductRef(
                        id = item.productId,
                        name = item.productName,
                        illustration = item.illustration,
                        formula = item.pricing.formula,
                        defaultUnit = item.pricing.width?.unit ?: item.pricing.height?.unit ?: LengthUnit.METER,
                    )
                    Transition(
                        state.copy(
                            step = FlowStep.ItemEntry,
                            pending = PendingItem(
                                product = product,
                                form = ItemEntryForm.fromPricing(item.pricing),
                                editingItemId = item.id,
                            ),
                        ),
                    )
                } else {
                    Transition(state)
                }
            }

            is FlowEvent.RemoveItemPressed ->
                if (step == FlowStep.ItemList && state.items.any { it.id == event.itemId }) {
                    Transition(state.copy(removalCandidate = event.itemId))
                } else {
                    Transition(state)
                }

            FlowEvent.RemoveConfirmed -> {
                val candidate = state.removalCandidate
                if (candidate != null) {
                    Transition(state.copy(items = state.items.filterNot { it.id == candidate }, removalCandidate = null))
                } else {
                    Transition(state)
                }
            }

            FlowEvent.RemoveCancelled -> Transition(state.copy(removalCandidate = null))

            FlowEvent.ContinueToReview ->
                if (step == FlowStep.ItemList) {
                    if (state.items.isEmpty()) {
                        Transition(state.copy(issue = FlowIssue.NO_ITEMS))
                    } else {
                        Transition(state.copy(step = FlowStep.Review, issue = null, depositProblem = null))
                    }
                } else {
                    Transition(state)
                }

            FlowEvent.GoToItems ->
                if (step == FlowStep.Review && !state.saving) {
                    Transition(state.copy(step = FlowStep.ItemList, confirmingSave = false, issue = null))
                } else {
                    Transition(state)
                }

            is FlowEvent.DepositEdited ->
                if (step == FlowStep.Review && !state.saving) {
                    Transition(state.copy(depositText = event.text, depositProblem = null))
                } else {
                    Transition(state)
                }

            FlowEvent.SavePressed -> savePressed(state)

            FlowEvent.SaveDismissed -> Transition(state.copy(confirmingSave = false))

            FlowEvent.SaveConfirmed -> saveConfirmed(state)

            is FlowEvent.SaveSucceeded ->
                Transition(state.copy(step = FlowStep.Saved(event.invoiceId), saving = false, issue = null))

            FlowEvent.SaveFailed ->
                // Nothing is lost: the items, customer and deposit are exactly as the user left them.
                Transition(state.copy(saving = false, confirmingSave = false, issue = FlowIssue.SAVE_FAILED))

            FlowEvent.BackPressed -> back(state)

            FlowEvent.ExitPressed -> requestExit(state)

            FlowEvent.ExitKeepDraft ->
                if (state.confirmingExit && state.mode == FlowMode.New) {
                    Transition(state.copy(confirmingExit = false), FlowEffect.Exit(keepDraft = true))
                } else {
                    Transition(state)
                }

            FlowEvent.ExitDiscard ->
                if (state.confirmingExit) {
                    Transition(state.copy(confirmingExit = false), FlowEffect.Exit(keepDraft = false))
                } else {
                    Transition(state)
                }

            FlowEvent.ExitCancelled -> Transition(state.copy(confirmingExit = false))
        }
    }

    private fun submitEntry(state: FlowState): Transition {
        val pending = state.pending
        if (state.step != FlowStep.ItemEntry || pending == null) return Transition(state)
        val evaluation = pending.form.evaluate()
        val pricing = evaluation.pricing
        return if (pricing == null) {
            Transition(state.copy(pending = pending.copy(showErrors = true, confirmedPricing = null)))
        } else {
            Transition(
                state.copy(
                    step = FlowStep.ItemConfirm,
                    pending = pending.copy(showErrors = false, confirmedPricing = pricing),
                ),
            )
        }
    }

    private fun confirmItem(state: FlowState): Transition {
        val pending = state.pending
        val pricing = pending?.confirmedPricing
        if (state.step != FlowStep.ItemConfirm || pending == null || pricing == null) return Transition(state)

        val editingId = pending.editingItemId
        val items = if (editingId == null) {
            // Every addition is a brand-new, independent item, even for the same product.
            state.items + InvoiceItem(
                id = newId(),
                productId = pending.product.id,
                productName = pending.product.name,
                illustration = pending.product.illustration,
                pricing = pricing,
            )
        } else {
            state.items.map { if (it.id == editingId) it.copy(pricing = pricing) else it }
        }
        // Always back to the item list: never the catalog, never a new invoice, never payment.
        return Transition(state.copy(step = FlowStep.ItemList, items = items, pending = null, issue = null))
    }

    private fun savePressed(state: FlowState): Transition {
        if (state.step != FlowStep.Review || state.saving) return Transition(state)
        if (state.items.isEmpty()) return Transition(state.copy(issue = FlowIssue.NO_ITEMS))
        return when (val parsed = PaymentCalculator.parseDeposit(state.depositText, state.total)) {
            is DepositParse.Invalid -> Transition(state.copy(depositProblem = parsed.problem, confirmingSave = false))
            is DepositParse.Valid -> Transition(state.copy(depositProblem = null, confirmingSave = true, issue = null))
        }
    }

    private fun saveConfirmed(state: FlowState): Transition {
        if (state.step != FlowStep.Review || !state.confirmingSave || state.saving) return Transition(state)
        return when (val parsed = PaymentCalculator.parseDeposit(state.depositText, state.total)) {
            is DepositParse.Invalid -> Transition(state.copy(depositProblem = parsed.problem, confirmingSave = false))
            is DepositParse.Valid -> {
                val draft = InvoiceDraft(
                    replacingInvoiceId = (state.mode as? FlowMode.Edit)?.invoiceId,
                    customer = state.customer.normalized(),
                    items = state.items,
                    deposit = parsed.deposit,
                )
                Transition(state.copy(confirmingSave = false, saving = true, issue = null), FlowEffect.Save(draft))
            }
        }
    }

    private fun back(state: FlowState): Transition {
        if (state.saving) return Transition(state)
        // An open dialog is dismissed first.
        if (state.confirmingExit) return Transition(state.copy(confirmingExit = false))
        if (state.confirmingSave) return Transition(state.copy(confirmingSave = false))
        if (state.removalCandidate != null) return Transition(state.copy(removalCandidate = null))

        return when (state.step) {
            FlowStep.Customer -> requestExit(state)
            FlowStep.ItemList ->
                if (state.mode == FlowMode.New) Transition(state.copy(step = FlowStep.Customer, issue = null)) else requestExit(state)
            FlowStep.Catalog -> Transition(state.copy(step = FlowStep.ItemList))
            FlowStep.ItemEntry ->
                if (state.pending?.editingItemId != null) {
                    // Leaving the details of an item being edited only abandons that edit.
                    Transition(state.copy(step = FlowStep.ItemList, pending = null))
                } else {
                    Transition(state.copy(step = FlowStep.Catalog))
                }
            FlowStep.ItemConfirm -> Transition(
                state.copy(step = FlowStep.ItemEntry, pending = state.pending?.copy(confirmedPricing = null)),
            )
            FlowStep.Review -> Transition(state.copy(step = FlowStep.ItemList))
            is FlowStep.Saved -> Transition(state, FlowEffect.Exit(keepDraft = false))
        }
    }

    private fun requestExit(state: FlowState): Transition {
        if (state.saving) return Transition(state)
        return if (state.isDirty) {
            Transition(state.copy(confirmingExit = true))
        } else {
            Transition(state, FlowEffect.Exit(keepDraft = false))
        }
    }
}
