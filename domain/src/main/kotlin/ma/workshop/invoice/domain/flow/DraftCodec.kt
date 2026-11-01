package ma.workshop.invoice.domain.flow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ma.workshop.invoice.domain.backup.ItemDto
import ma.workshop.invoice.domain.backup.toDomain
import ma.workshop.invoice.domain.backup.toDto
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.ProductRef
import ma.workshop.invoice.domain.pricing.ItemEntryForm
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.Measurements

@Serializable
private data class ProductRefDto(
    val id: Long? = null,
    val name: String,
    val illustration: String,
    val formula: String,
    val defaultUnit: String,
    val measurements: String? = null,
)

@Serializable
private data class FormDto(
    val formula: String,
    val widthText: String,
    val widthUnit: String,
    val heightText: String,
    val heightUnit: String,
    val quantity: Int,
    val rateText: String,
    val measurements: String? = null,
    val rateNeedsConfirmation: Boolean = false,
)

@Serializable
private data class PendingDto(
    val product: ProductRefDto,
    val form: FormDto,
    val editingItemId: String? = null,
    val additionalForms: List<FormDto> = emptyList(),
)

private fun ItemEntryForm.toDto() = FormDto(
    formula.id, widthText, widthUnit.id, heightText, heightUnit.id, quantity, rateText,
    measurements.id, rateNeedsConfirmation,
)

private fun FormDto.toForm(): ItemEntryForm? {
    val method = PricingFormula.fromId(formula) ?: return null
    return ItemEntryForm(
        method, widthText, LengthUnit.fromId(widthUnit) ?: return null,
        heightText, LengthUnit.fromId(heightUnit) ?: return null, quantity, rateText,
        measurements?.let { Measurements.fromId(it) ?: return null } ?: method.measurements,
        rateNeedsConfirmation,
    )
}

@Serializable
private data class DraftDto(
    val version: Int = 1,
    val customerName: String,
    val customerPhone: String,
    val depositText: String,
    val items: List<ItemDto>,
    val pending: PendingDto? = null,
)

/**
 * Auto-saves a *new* invoice that has not been saved yet, so a phone call, a low-memory kill or an
 * accidental exit never loses the user's work ("never silently discard unsaved edits").
 * The restored state lands on the item list (or on the item details if one was being entered).
 */
object DraftCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Null when there is nothing worth saving, or when editing an existing invoice (the original is intact). */
    fun encode(state: FlowState): String? {
        if (state.mode != FlowMode.New || !state.isDirty || state.step is FlowStep.Saved) return null
        val pending = state.pending?.let { p ->
            PendingDto(
                product = ProductRefDto(
                    p.product.id, p.product.name, p.product.illustration, p.product.formula.id, p.product.defaultUnit.id, p.product.measurements.id,
                ),
                form = p.form.toDto(),
                editingItemId = p.editingItemId,
                additionalForms = p.additionalForms.map { it.toDto() },
            )
        }
        return json.encodeToString(
            DraftDto.serializer(),
            DraftDto(
                customerName = state.customer.name,
                customerPhone = state.customer.phone,
                depositText = state.depositText,
                items = state.items.map { it.toDto() },
                pending = pending,
            ),
        )
    }

    /** Null if the stored draft is unreadable; the caller should then simply start a fresh invoice. */
    fun decode(text: String): FlowState? {
        val dto = try {
            json.decodeFromString(DraftDto.serializer(), text)
        } catch (e: Exception) {
            return null
        }
        val items = dto.items.map { it.toDomain() ?: return null }
        val pending = dto.pending?.let { p ->
            val formula = PricingFormula.fromId(p.product.formula) ?: return null
            val defaultUnit = LengthUnit.fromId(p.product.defaultUnit) ?: return null
            val form = p.form.toForm() ?: return null
            val additional = p.additionalForms.map { it.toForm() ?: return null }
            if ((listOf(form) + additional).any { it.formula != formula }) return null
            PendingItem(
                product = ProductRef(p.product.id, p.product.name, p.product.illustration, formula, defaultUnit,
                    p.product.measurements?.let { Measurements.fromId(it) ?: return null } ?: formula.measurements),
                form = form,
                editingItemId = p.editingItemId,
                additionalForms = additional,
            )
        }
        val step: FlowStep = when {
            pending != null -> FlowStep.ItemEntry
            items.isEmpty() -> FlowStep.Customer
            else -> FlowStep.ItemList
        }
        return FlowState(
            mode = FlowMode.New,
            step = step,
            customer = CustomerInfo(dto.customerName, dto.customerPhone),
            items = items,
            pending = pending,
            depositText = dto.depositText,
        )
    }
}
