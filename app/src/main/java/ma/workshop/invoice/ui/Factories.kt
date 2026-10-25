package ma.workshop.invoice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Tiny factory so ViewModels can take constructor arguments without a DI framework. */
class VmFactory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}
