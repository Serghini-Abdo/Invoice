package ma.workshop.invoice.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.domain.invoice.HistoryFilter
import ma.workshop.invoice.domain.invoice.HistoryQuery
import ma.workshop.invoice.domain.invoice.InvoiceSummary
import java.time.ZoneId

class HistoryViewModel(container: AppContainer) : ViewModel() {

    private val all: StateFlow<List<InvoiceSummary>> =
        container.invoices.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query = MutableStateFlow(HistoryQuery())

    val visible: StateFlow<List<InvoiceSummary>> =
        combine(all, query) { list, q -> HistoryFilter.apply(list, q, ZoneId.systemDefault()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = all.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
