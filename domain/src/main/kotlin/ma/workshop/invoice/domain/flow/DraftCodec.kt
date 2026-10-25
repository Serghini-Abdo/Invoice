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

@Serializable
private data class ProductRefDto(
    val id: Long? = null,
    val name: String,
    val illustration: String,
    val formula: String,
    val defaultUnit: String,
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
)

@Serializable
private data class PendingDto(val product: ProductRefDto, val form: FormDto, val editingItemId: String? = null)

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
                    p.product.id, p.product.name, p.product.illustration, p.product.formula.id, p.product.defaultUnit.id,
                ),
                form = FormDto(
                    p.form.formula.id, p.form.widthText, p.form.widthUnit.id, p.form.heightText, p.form.heightUnit.id,
                    p.form.quantity, p.form.rateText,
                ),
                editingItemId = p.editingItemId,
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
            val formFormula = PricingFormula.fromId(p.form.formula) ?: return null
            PendingItem(
                product = ProductRef(p.product.id, p.product.name, p.product.illustration, formula, defaultUnit),
                form = ItemEntryForm(
                    formula = formFormula,
                    widthText = p.form.widthText,
                    widthUnit = LengthUnit.fromId(p.form.widthUnit) ?: return null,
                    heightText = p.form.heightText,
                    heightUnit = LengthUnit.fromId(p.form.heightUnit) ?: return null,
                    quantity = p.form.quantity,
                    rateText = p.form.rateText,
                ),
                editingItemId = p.editingItemId,
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
