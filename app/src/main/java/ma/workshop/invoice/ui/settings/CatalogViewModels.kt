package ma.workshop.invoice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula

class CatalogViewModel(private val container: AppContainer) : ViewModel() {

    val products: StateFlow<List<ProductDefinition>> =
        container.products.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch { container.products.setActive(id, active) }
    }
}

/** Editable fields of one product. */
data class ProductForm(
    val name: String = "",
    val illustration: String = "door",
    val formula: PricingFormula = PricingFormula.AREA,
    val defaultUnit: LengthUnit = LengthUnit.METER,
    val category: String = "",
    val isActive: Boolean = true,
)

sealed interface ProductEditState {
    data object Loading : ProductEditState
    data object NotFound : ProductEditState
    data class Editing(val form: ProductForm, val isNew: Boolean, val nameMissing: Boolean = false) : ProductEditState
}

/** Creates or edits a catalog product. Historical invoices hold snapshots, so nothing here can change them. */
class ProductEditViewModel(private val container: AppContainer, private val productId: Long) : ViewModel() {

    private val _state = MutableStateFlow<ProductEditState>(ProductEditState.Loading)
    val state: StateFlow<ProductEditState> = _state.asStateFlow()

    private val exitChannel = Channel<Unit>(Channel.BUFFERED)
    val exits = exitChannel.receiveAsFlow()

    private var existing: ProductDefinition? = null

    init {
        viewModelScope.launch {
            if (productId == 0L) {
                _state.value = ProductEditState.Editing(ProductForm(), isNew = true)
            } else {
                val product = container.products.get(productId)
                existing = product
                _state.value = if (product == null) {
                    ProductEditState.NotFound
                } else {
                    ProductEditState.Editing(
                        ProductForm(
                            name = product.name,
                            illustration = product.illustration,
                            formula = product.formula,
                            defaultUnit = product.defaultUnit,
                            category = product.category.orEmpty(),
                            isActive = product.isActive,
                        ),
                        isNew = false,
                    )
                }
            }
        }
    }

    val photoFailed = MutableStateFlow(false)

    /** Copies the picked photo into the app and makes it the product picture. */
    fun importPhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { container.photos.import(uri) }
            if (key == null) {
                photoFailed.value = true
            } else {
                photoFailed.value = false
                edit { it.copy(illustration = key) }
            }
        }
    }

    fun edit(transform: (ProductForm) -> ProductForm) {
        val current = _state.value as? ProductEditState.Editing ?: return
        _state.value = current.copy(form = transform(current.form), nameMissing = false)
    }

    fun save() {
        val current = _state.value as? ProductEditState.Editing ?: return
        val form = current.form
        if (form.name.isBlank()) {
            _state.value = current.copy(nameMissing = true)
            return
        }
        viewModelScope.launch {
            val base = existing
            val definition = ProductDefinition(
                id = base?.id ?: 0,
                name = form.name.trim(),
                illustration = form.illustration,
                formula = form.formula,
                defaultUnit = form.defaultUnit,
                category = form.category.trim().ifEmpty { null },
                isActive = form.isActive,
                sortOrder = base?.sortOrder ?: 0,
                createdAt = base?.createdAt ?: 0,
                updatedAt = 0,
            )
            container.products.save(definition)
            withContext(Dispatchers.IO) { container.sweepPhotos() }
            exitChannel.trySend(Unit)
        }
    }

    fun delete() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            container.products.delete(id)
            withContext(Dispatchers.IO) { container.sweepPhotos() }
            exitChannel.trySend(Unit)
        }
    }

    fun deactivateInstead() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            container.products.setActive(id, false)
            exitChannel.trySend(Unit)
        }
    }
}
