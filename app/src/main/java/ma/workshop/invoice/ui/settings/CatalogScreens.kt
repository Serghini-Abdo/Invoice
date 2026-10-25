package ma.workshop.invoice.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.catalog.Illustrations
import ma.workshop.invoice.domain.catalog.PhotoKey
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ConfirmDialog
import ma.workshop.invoice.ui.components.IllustrationImage
import ma.workshop.invoice.ui.components.AppTextField
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.components.UnitToggle
import ma.workshop.invoice.ui.components.strictClickable
import ma.workshop.invoice.ui.methodName
import ma.workshop.invoice.ui.theme.appPalette

/** Manage the workshop's own product types. Inactive products stay listed (dimmed) and keep their history. */
@Composable
fun CatalogManagementScreen(
    products: List<ProductDefinition>,
    onSetActive: (Long, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.CATALOG_TITLE, onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(products, key = { it.id }) { product ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .strictClickable(role = Role.Button, onClickLabel = Strings.EDIT_PRODUCT) { onEdit(product.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (product.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = if (product.isActive) 2.dp else 0.dp,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.strictClickable(role = Role.Switch) { onSetActive(product.id, !product.isActive) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Switch(checked = product.isActive, onCheckedChange = null)
                            ArabicText(
                                if (product.isActive) Strings.ACTIVE else Strings.INACTIVE,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (product.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            ArabicText(
                                product.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (product.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ArabicText(product.formula.methodName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        IllustrationImage(product.illustration, Modifier.size(64.dp))
                    }
                }
            }
        }
        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            BigButton(Strings.NEW_PRODUCT, Icons.Filled.Add, onAdd, Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun FormulaOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .strictClickable(role = Role.RadioButton, onClickLabel = title, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                ArabicText(title, style = MaterialTheme.typography.titleMedium)
                ArabicText(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
fun ProductEditScreen(
    state: ProductEditState,
    onEdit: ((ProductForm) -> ProductForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDeactivateInstead: () -> Unit,
    onPickPhoto: (android.net.Uri) -> Unit,
    photoFailed: Boolean,
    onBack: () -> Unit,
) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickPhoto(uri)
    }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            ProductEditState.Loading -> {
                AppTopBar(Strings.EDIT_PRODUCT, onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            ProductEditState.NotFound -> {
                AppTopBar(Strings.EDIT_PRODUCT, onBack)
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    ArabicText(Strings.INVOICE_NOT_FOUND, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                }
            }
            is ProductEditState.Editing -> {
                val form = state.form
                AppTopBar(if (state.isNew) Strings.NEW_PRODUCT else Strings.EDIT_PRODUCT, onBack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusBanner(Strings.NO_PRICE_NOTE, BannerKind.Info)

                    AppTextField(
                        value = form.name,
                        onValueChange = { v -> onEdit { it.copy(name = v.take(40)) } },
                        label = Strings.PRODUCT_NAME,
                        error = if (state.nameMissing) Strings.NAME_REQUIRED else null,
                        tag = "product-name",
                    )

                    SectionCard {
                        ArabicText(Strings.PRODUCT_PICTURE, style = MaterialTheme.typography.titleMedium)
                        if (PhotoKey.isPhoto(form.illustration)) {
                            IllustrationImage(form.illustration, Modifier.size(120.dp).testTag("product-photo"))
                        }
                        BigButton(
                            Strings.PICK_PHOTO,
                            Icons.Filled.AddPhotoAlternate,
                            { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            Modifier.testTag("pick-photo"),
                            kind = ButtonKind.Secondary,
                        )
                        if (photoFailed) StatusBanner(Strings.PHOTO_FAILED, BannerKind.Warning)
                        val pictures = Illustrations.all
                        for (row in pictures.chunked(4)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // keep the last row aligned with the others; the first picture stays top-right
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                for (picture in row.reversed()) {
                                    val selected = picture.key == form.illustration
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .strictClickable(role = Role.RadioButton, onClickLabel = picture.nameAr) {
                                                onEdit { it.copy(illustration = picture.key) }
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            if (selected) 3.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                    ) {
                                        Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                                            IllustrationImage(picture.key, Modifier.fillMaxSize(), contentDescription = picture.nameAr)
                                            if (selected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.appPalette.onColor,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(22.dp)
                                                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SectionCard {
                        ArabicText(Strings.PRICING_METHOD, style = MaterialTheme.typography.titleMedium)
                        FormulaOption(Strings.FORMULA_AREA, Strings.FORMULA_AREA_HINT, form.formula == PricingFormula.AREA) {
                            onEdit { it.copy(formula = PricingFormula.AREA) }
                        }
                        FormulaOption(Strings.FORMULA_PERIMETER, Strings.FORMULA_PERIMETER_HINT, form.formula == PricingFormula.PERIMETER) {
                            onEdit { it.copy(formula = PricingFormula.PERIMETER) }
                        }
                        FormulaOption(Strings.FORMULA_PIECE, Strings.FORMULA_PIECE_HINT, form.formula == PricingFormula.PER_ITEM) {
                            onEdit { it.copy(formula = PricingFormula.PER_ITEM) }
                        }
                        if (!state.isNew) {
                            ArabicText(Strings.FORMULA_LOCKED_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (form.formula.needsDimensions) {
                        SectionCard {
                            ArabicText(Strings.DEFAULT_UNIT, style = MaterialTheme.typography.titleMedium)
                            UnitToggle(form.defaultUnit, { unit -> onEdit { it.copy(defaultUnit = unit) } })
                        }
                    }

                    AppTextField(
                        value = form.category,
                        onValueChange = { v -> onEdit { it.copy(category = v.take(30)) } },
                        label = Strings.CATEGORY,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        tag = "product-category",
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .strictClickable(role = Role.Switch) { onEdit { it.copy(isActive = !it.isActive) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(checked = form.isActive, onCheckedChange = null)
                        ArabicText(if (form.isActive) Strings.ACTIVE else Strings.INACTIVE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BigButton(Strings.SAVE, Icons.Filled.Save, onSave, kind = ButtonKind.Success)
                    if (!state.isNew) {
                        BigButton(Strings.DELETE, Icons.Filled.Delete, { confirmingDelete = true }, kind = ButtonKind.Danger)
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = Strings.DELETE_PRODUCT_TITLE,
            message = Strings.DELETE_PRODUCT_MESSAGE,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
            confirmText = Strings.DELETE,
            danger = true,
            extra = {
                BigButton(Strings.DEACTIVATE_INSTEAD, Icons.Filled.Check, {
                    confirmingDelete = false
                    onDeactivateInstead()
                }, kind = ButtonKind.Secondary)
            },
        )
    }
}
