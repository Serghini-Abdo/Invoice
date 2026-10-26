package ma.workshop.invoice.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import ma.workshop.invoice.ui.components.LocalSpeakers
import ma.workshop.invoice.ui.components.RoundSpeaker
import ma.workshop.invoice.domain.format.ArabicTerms
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.invoice.HistoryQuery
import ma.workshop.invoice.domain.invoice.InvoiceSummary
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.AppTextField
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.StrictButton
import ma.workshop.invoice.ui.components.StrictIconButton
import ma.workshop.invoice.ui.components.strictClickable
import ma.workshop.invoice.ui.theme.appPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    invoices: List<InvoiceSummary>,
    totalCount: Int,
    query: HistoryQuery,
    onQueryChange: (HistoryQuery) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.HISTORY_TITLE, onBack)

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = query.text,
                onValueChange = { onQueryChange(query.copy(text = it)) },
                label = null,
                placeholder = Strings.SEARCH_HINT,
                icon = Icons.Filled.Search,
                trailing = {
                    if (query.text.isNotEmpty()) {
                        StrictIconButton(
                            onClick = { onQueryChange(query.copy(text = "")) },
                            contentDescription = Strings.CLEAR_FILTERS,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = Strings.CLEAR_FILTERS)
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                tag = "history-search",
            )
            // "From" on the right, "to" beside it, the clear button on the left.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!query.isEmpty) {
                    StrictIconButton(
                        onClick = { onQueryChange(HistoryQuery()) },
                        contentDescription = Strings.CLEAR_FILTERS,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = Strings.CLEAR_FILTERS, modifier = Modifier.size(28.dp))
                    }
                }
                DateButton(Strings.DATE_TO, query.toDate, Modifier.weight(1f)) { pickingTo = true }
                DateButton(Strings.DATE_FROM, query.fromDate, Modifier.weight(1f)) { pickingFrom = true }
            }
        }

        if (invoices.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                ArabicText(
                    if (totalCount == 0) Strings.NO_INVOICES else Strings.NO_RESULTS,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(invoices, key = { it.id }) { invoice ->
                    val ui = LocalSpeakers.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundSpeaker(
                            listOf(
                                "${ArabicTerms.INVOICE} ${ArabicTerms.NUMBER} ${ArabicFormat.invoiceNumber(invoice.number)}",
                                invoice.customerName.trim(),
                                "${ArabicTerms.TOTAL} ${ui.texts.amount(invoice.total)}",
                            ).filter { it.isNotEmpty() }.joinToString("، "),
                        )
                        Box(Modifier.weight(1f)) { InvoiceSummaryCard(invoice, zone) { onOpen(invoice.id) } }
                    }
                }
            }
        }
    }

    if (pickingFrom) {
        DateDialog(initial = query.fromDate, onPick = { onQueryChange(query.copy(fromDate = it)) }, onDismiss = { pickingFrom = false })
    }
    if (pickingTo) {
        DateDialog(initial = query.toDate, onPick = { onQueryChange(query.copy(toDate = it)) }, onDismiss = { pickingTo = false })
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    StrictButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ArabicText(
            text = date?.let { ArabicFormat.date(it) } ?: label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate?, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            StrictButton(
                onClick = {
                    // The picker works in UTC midnight; convert back to a plain calendar date.
                    onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { ArabicText(Strings.CONFIRM, style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = {
            StrictButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                ArabicText(Strings.CANCEL, style = MaterialTheme.typography.titleMedium)
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun InvoiceSummaryCard(invoice: InvoiceSummary, zone: ZoneId, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .strictClickable(role = Role.Button, onClickLabel = Strings.INVOICE_DETAILS, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        // Amounts on the left, the invoice and customer on the right.
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.Start) {
                ArabicText(ArabicFormat.amount(invoice.total), style = MaterialTheme.typography.titleLarge)
                if (invoice.remaining.isZero) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArabicText(Strings.FULLY_PAID, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.appPalette.paid)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.appPalette.paid, modifier = Modifier.size(20.dp))
                    }
                } else {
                    ArabicText(
                        "${Strings.REMAINING}: ${ArabicFormat.amount(invoice.remaining)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                ArabicText(
                    "${Strings.INVOICE_NUMBER} ${ArabicFormat.invoiceNumber(invoice.number)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                ArabicText(
                    invoice.customerName.ifBlank { Strings.ANONYMOUS_CUSTOMER },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ArabicText(
                    "${ArabicFormat.date(invoice.createdAt, zone)}  •  ${invoice.itemCount} ${Strings.ITEMS_COUNT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (invoice.revision > 1) {
                    ArabicText(Strings.EDITED, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.appPalette.onWarningContainer)
                }
            }
        }
    }
}
