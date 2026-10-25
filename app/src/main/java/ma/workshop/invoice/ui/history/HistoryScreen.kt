package ma.workshop.invoice.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import ma.workshop.invoice.ui.components.ScreenHeader
import ma.workshop.invoice.ui.theme.AppColors
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
        ScreenHeader(Strings.HISTORY_TITLE, onBack)

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query.text,
                onValueChange = { onQueryChange(query.copy(text = it)) },
                placeholder = { Text(Strings.SEARCH_HINT, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.text.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange(query.copy(text = "")) }) {
                            Icon(Icons.Filled.Clear, contentDescription = Strings.CLEAR_FILTERS)
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DateButton(Strings.DATE_FROM, query.fromDate, Modifier.weight(1f)) { pickingFrom = true }
                DateButton(Strings.DATE_TO, query.toDate, Modifier.weight(1f)) { pickingTo = true }
                if (!query.isEmpty) {
                    IconButton(onClick = { onQueryChange(HistoryQuery()) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Clear, contentDescription = Strings.CLEAR_FILTERS, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        if (invoices.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
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
                    InvoiceSummaryCard(invoice, zone) { onOpen(invoice.id) }
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = date?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            TextButton(onClick = {
                // The picker works in UTC midnight; convert back to a plain calendar date.
                onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                onDismiss()
            }) { Text(Strings.CONFIRM, style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL, style = MaterialTheme.typography.titleMedium) }
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
            .clickable(role = Role.Button, onClickLabel = Strings.INVOICE_DETAILS, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${Strings.INVOICE_NUMBER} ${ArabicFormat.invoiceNumber(invoice.number)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    invoice.customerName.ifBlank { Strings.ANONYMOUS_CUSTOMER },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${ArabicFormat.date(invoice.createdAt, zone)}  •  ${invoice.itemCount} ${Strings.ITEMS_COUNT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (invoice.revision > 1) {
                    Text(Strings.EDITED, style = MaterialTheme.typography.labelMedium, color = AppColors.Amber)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(ArabicFormat.amount(invoice.total), style = MaterialTheme.typography.titleLarge)
                if (invoice.remaining.isZero) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Green, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Strings.FULLY_PAID, style = MaterialTheme.typography.titleSmall, color = AppColors.Green)
                    }
                } else {
                    Text(
                        "${Strings.REMAINING}: ${ArabicFormat.amount(invoice.remaining)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
