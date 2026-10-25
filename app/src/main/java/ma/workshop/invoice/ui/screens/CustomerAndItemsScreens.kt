package ma.workshop.invoice.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.flow.FlowIssue
import ma.workshop.invoice.domain.flow.FlowState
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.format.ArabicTerms
import ma.workshop.invoice.domain.format.ItemDescriber
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.AmountRow
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ConfirmDialog
import ma.workshop.invoice.ui.components.IllustrationImage
import ma.workshop.invoice.ui.components.ProductCard
import ma.workshop.invoice.ui.components.AddProductButton
import ma.workshop.invoice.ui.components.AppTextField
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.components.StrictButton
import ma.workshop.invoice.ui.components.strictClickable
import ma.workshop.invoice.ui.theme.appPalette
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api

/** Step 1: optional customer details. Both fields can stay empty; continuing is always allowed. */
@Composable
fun CustomerScreen(
    customer: CustomerInfo,
    onChange: (name: String, phone: String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.CUSTOMER_TITLE, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBanner(Strings.CUSTOMER_HINT, BannerKind.Info)
            AppTextField(
                value = customer.name,
                onValueChange = { onChange(it.take(60), customer.phone) },
                label = Strings.CUSTOMER_NAME,
                hint = "(${Strings.OPTIONAL})",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                tag = "customer-name",
            )
            AppTextField(
                value = customer.phone,
                onValueChange = { v -> onChange(customer.name, v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20)) },
                label = Strings.CUSTOMER_PHONE,
                hint = "(${Strings.OPTIONAL})",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                tag = "customer-phone",
            )
        }
        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            BigButton(
                text = Strings.CONTINUE,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = {
                    focus.clearFocus()
                    onContinue()
                },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** One invoice item with its thumbnail, key measurements and total, plus edit and remove. */
@Composable
fun ItemCard(item: InvoiceItem, onEdit: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Amount on the left, product on the right, as on the printed invoice.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArabicText(
                    ArabicFormat.amount(item.pricing.total),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    ArabicText(item.productName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ItemDescriber.measures(item.pricing)?.let {
                        ArabicText(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    ArabicText(
                        "${ArabicTerms.QUANTITY}: ${item.pricing.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                IllustrationImage(item.illustration, Modifier.size(64.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Strings.DELETE, Icons.Filled.Delete, onRemove, Modifier.weight(1f), danger = true)
                SmallAction(Strings.EDIT, Icons.Filled.Edit, onEdit, Modifier.weight(1f))
            }
        }
    }
}

/** Category filter. The selected one is filled, so the choice never relies on colour alone. */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    StrictButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ArabicText(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SmallAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier, danger: Boolean = false) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .strictClickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = text, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (danger) MaterialTheme.appPalette.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (danger) MaterialTheme.appPalette.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            ArabicText(text, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/** Step 2: the item list. Confirming any item always lands here. */
@Composable
fun ItemListScreen(
    state: FlowState,
    isEditing: Boolean,
    onAdd: () -> Unit,
    onEditItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onEditCustomer: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSpeakAdd: () -> Unit = {},
    speechAvailable: Boolean = true,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(if (isEditing) Strings.EDIT else Strings.ITEMS_TITLE, onBack)

        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .strictClickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = Strings.ADD_PRODUCT, onClick = onAdd),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IllustrationImage("door", Modifier.size(120.dp))
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        ArabicText(Strings.ADD_FIRST_PRODUCT, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        ArabicText(Strings.NO_ITEMS_HINT, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "customer") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .strictClickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = Strings.EDIT_CUSTOMER, onClick = onEditCustomer),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Edit, contentDescription = Strings.EDIT, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            val summary = listOf(state.customer.name.trim(), state.customer.phone.trim()).filter { it.isNotEmpty() }
                            ArabicText(
                                if (summary.isEmpty()) "${Strings.EDIT_CUSTOMER} (${Strings.OPTIONAL})" else summary.joinToString("  •  "),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    ItemCard(item, onEdit = { onEditItem(item.id) }, onRemove = { onRemoveItem(item.id) })
                }
            }
        }

        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.items.isNotEmpty()) {
                    AmountRow(Strings.INVOICE_TOTAL, ArabicFormat.amount(state.total), emphasized = true)
                }
                if (state.issue == FlowIssue.NO_ITEMS) {
                    StatusBanner(Strings.NEED_ONE_ITEM, BannerKind.Warning)
                }
                AddProductButton(onClick = onAdd, onSpeak = onSpeakAdd, speechAvailable = speechAvailable)
                BigButton(
                    Strings.CONTINUE,
                    Icons.AutoMirrored.Filled.ArrowForward,
                    onContinue,
                    enabled = state.items.isNotEmpty(),
                )
            }
        }
    }

    if (state.removalCandidate != null) {
        ConfirmDialog(
            title = Strings.REMOVE_ITEM_TITLE,
            message = Strings.REMOVE_ITEM_MESSAGE,
            onConfirm = onConfirmRemove,
            onDismiss = onCancelRemove,
            confirmText = Strings.DELETE,
            danger = true,
        )
    }
}

/**
 * The illustrated catalog: two columns where the width allows, picture first, name below, and a
 * separate speaker. A category filter appears only when products actually have categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogPickerScreen(
    products: List<ProductDefinition>,
    onPick: (ProductDefinition) -> Unit,
    onSpeak: (String) -> Unit,
    speechAvailable: Boolean,
    onBack: () -> Unit,
) {
    val categories = products.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (selectedCategory == null) products else products.filter { it.category?.trim() == selectedCategory }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.CHOOSE_PRODUCT, onBack)
        if (categories.size >= 2) {
            // reverseLayout: "all" first, on the right, and the rest towards the left.
            LazyRow(
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    CategoryChip(
                        label = Strings.ALL_CATEGORIES,
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                    )
                }
                items(categories) { category ->
                    CategoryChip(
                        label = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                ArabicText(Strings.NO_PRODUCTS, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        } else {
            // The one right-to-left island of the layout: the grid keeps today's order, first product top-right
            // (decided). Each card is laid out left-to-right again, like the rest of the app.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(shown, key = { it.id }) { product ->
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            ProductCard(
                                name = product.name,
                                illustration = product.illustration,
                                onSelect = { onPick(product) },
                                onSpeak = { onSpeak(product.name) },
                                speechAvailable = speechAvailable,
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.size(8.dp)) }
                }
            }
        }
    }
}
