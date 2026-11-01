package ma.workshop.invoice.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.material3.Icon

import ma.workshop.invoice.ui.components.CoinPicture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.flow.PendingItem
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.format.ItemDescriber
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.Field
import ma.workshop.invoice.domain.pricing.ItemEntryForm
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.PricingLimits
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.arabic
import ma.workshop.invoice.ui.components.AppTextField
import ma.workshop.invoice.ui.components.AppSubBar
import ma.workshop.invoice.ui.components.AudioToggle
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.IllustrationImage
import ma.workshop.invoice.ui.components.LocalSpeakers
import ma.workshop.invoice.ui.components.LocalUnitScript
import ma.workshop.invoice.ui.components.RoundSpeaker
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.StrictIconButton
import ma.workshop.invoice.ui.components.strictClickable
import ma.workshop.invoice.ui.components.FieldIcons
import androidx.compose.material.icons.filled.Inventory2
import kotlinx.coroutines.launch
import ma.workshop.invoice.ui.rateLabel
import ma.workshop.invoice.ui.rateUnit
import ma.workshop.invoice.ui.theme.appPalette

/** All variations and their confirmation share one page; the system owns numeric input. */
@Composable
fun ItemEntryScreen(
    pending: PendingItem,
    audioEnabled: Boolean,
    onToggleAudio: () -> Unit,
    onFormChange: (ItemEntryForm, Int) -> Unit,
    onAddVariation: () -> Unit,
    onRemoveVariation: (Int) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun dismissKeyboard() { keyboard?.hide(); focus.clearFocus() }
    BackHandler(enabled = keyboardVisible) { dismissKeyboard() }
    val forms = pending.forms
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val script = LocalUnitScript.current
    val speakers = LocalSpeakers.current
    val evaluations = forms.map { it.evaluate() }
    val total = if (evaluations.all { it.isValid }) evaluations.fold(Money.ZERO) { sum, entry -> sum + entry.pricing!!.total } else null

    fun validateAnd(action: () -> Unit) {
        dismissKeyboard()
        action()
        val invalid = evaluations.indexOfFirst { !it.isValid }
        val missingDescription = pending.isCustomCharge && pending.product.name.isBlank()
        if (invalid >= 0 || missingDescription) {
            val target = if (missingDescription) 0 else invalid + 1
            scope.launch { listState.animateScrollToItem(target) }
        }
    }

    // Lines the owner opened by hand. Adding or removing a size resets it: then only the newest line is open.
    val opened = remember { mutableStateMapOf<Int, Boolean>() }
    // The line the owner is typing in. Moving to another line closes the one that was reopened for editing.
    var activeCard by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(activeCard) {
        val active = activeCard ?: return@LaunchedEffect
        val toClose = forms.indices.filter { it != active && forms.getOrNull(it)?.evaluate()?.isValid == true && (opened[it] ?: (it == forms.lastIndex)) }
        if (toClose.isNotEmpty()) {
            toClose.forEach { opened[it] = false }
            listState.animateScrollToItem(active + 1)
        }
    }
    LaunchedEffect(forms.size) {
        opened.clear()
        activeCard = null
        if (forms.size > 1) listState.animateScrollToItem(forms.size)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        AppTopBar(
            title = if (pending.isCustomCharge) Strings.CUSTOM_CHARGE else pending.product.name,
            onBack = { if (keyboardVisible) dismissKeyboard() else onBack() },
            centerIcon = { IllustrationImage(pending.product.illustration, Modifier.size(36.dp)) },
        )
        if (keyboardVisible) {
            BigButton(Strings.HIDE_KEYBOARD, Icons.Filled.KeyboardHide, { dismissKeyboard() },
                Modifier.padding(horizontal = 16.dp).testTag("hide-keyboard"), kind = ButtonKind.Secondary, spokenTile = false)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("entry-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (pending.isCustomCharge) {
                    AppTextField(
                        value = pending.product.name,
                        onValueChange = onDescriptionChange,
                        label = Strings.CHARGE_DESCRIPTION,
                        error = if (pending.showErrors && pending.product.name.isBlank()) Strings.NAME_REQUIRED else null,
                        tag = "charge-description",
                    )
                }
            }
            itemsIndexed(forms) { index, form ->
                val evaluation = evaluations[index]
                fun tag(field: String) = if (index == 0) "field-$field" else "field-$field-$index"
                fun change(next: ItemEntryForm) = onFormChange(next, index)
                fun error(field: Field) = if (pending.showErrors) evaluation.issueFor(field)?.arabic() else null
                val compact = forms.size > 1 && evaluation.isValid && !(opened[index] ?: (index == forms.lastIndex))
                // The keyboard's action key walks width, height, rate, quantity; only the last one closes it.
                val widthFocus = remember { FocusRequester() }
                val heightFocus = remember { FocusRequester() }
                val rateFocus = remember { FocusRequester() }
                val quantityFocus = remember { FocusRequester() }
                val order = buildList {
                    if (form.visibleMeasurements.width) add(widthFocus)
                    if (form.visibleMeasurements.height) add(heightFocus)
                    add(rateFocus)
                    if (form.formula.usesQuantity) add(quantityFocus)
                }
                fun optionsFor(field: FocusRequester, type: KeyboardType) =
                    KeyboardOptions(keyboardType = type, imeAction = if (order.last() == field) ImeAction.Done else ImeAction.Next)
                fun actionsFor(field: FocusRequester) = KeyboardActions(
                    onNext = { order.getOrNull(order.indexOf(field) + 1)?.requestFocus() },
                    onDone = { dismissKeyboard() },
                )
                if (compact) CompactLine(index, evaluation.pricing!!) {
                    // One line open at a time: opening this one closes the others (a blank new line stays open).
                    forms.indices.forEach { opened[it] = (it == index) }
                }
                else SectionCard(modifier = Modifier.testTag("variation-$index").onFocusChanged { if (it.hasFocus) activeCard = index }) {
                    if (forms.size > 1) {
                        ArabicText("${Strings.LINE} ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    }
                    if (form.visibleMeasurements.width) {
                        AppTextField(form.widthText, { change(form.copy(widthText = it.take(30))) }, Strings.WIDTH,
                            illustration = FieldIcons.Width,
                            hint = ArabicFormat.unit(form.widthUnit, script), error = error(Field.WIDTH),
                            keyboardOptions = optionsFor(widthFocus, KeyboardType.Decimal), keyboardActions = actionsFor(widthFocus),
                            tag = tag("width"), focusRequester = widthFocus)
                    }
                    if (form.visibleMeasurements.height) {
                        AppTextField(form.heightText, { change(form.copy(heightText = it.take(30))) }, Strings.HEIGHT,
                            illustration = FieldIcons.Height,
                            hint = ArabicFormat.unit(form.heightUnit, script), error = error(Field.HEIGHT),
                            keyboardOptions = optionsFor(heightFocus, KeyboardType.Decimal), keyboardActions = actionsFor(heightFocus),
                            tag = tag("height"), focusRequester = heightFocus)
                    }
                    AppTextField(form.rateText, { change(form.copy(rateText = it.take(30))) }, form.formula.rateLabel(),
                        hint = form.formula.rateUnit(script), error = error(Field.RATE),
                        keyboardOptions = optionsFor(rateFocus, KeyboardType.Decimal), keyboardActions = actionsFor(rateFocus),
                        tag = tag("rate"), focusRequester = rateFocus, picture = { CoinPicture() },
                        // The carried-over price shows in amber text on the normal white box (not a yellow box).
                        contentColor = if (form.rateNeedsConfirmation) MaterialTheme.appPalette.balanceBorder else MaterialTheme.colorScheme.onSurface)
                    if (form.rateNeedsConfirmation) {
                        ArabicText(Strings.SAME_PRICE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("same-price-$index"))
                    }
                    if (form.formula.usesQuantity) {
                        AppTextField(if (form.quantity == 0) "" else form.quantity.toString(),
                            { change(form.copy(quantity = it.toIntOrNull() ?: 0)) }, Strings.QUANTITY,
                            illustration = Icons.Filled.Inventory2,
                            error = error(Field.QUANTITY), tag = tag("quantity"),
                            keyboardOptions = optionsFor(quantityFocus, KeyboardType.Number), keyboardActions = actionsFor(quantityFocus),
                            focusRequester = quantityFocus,
                            startButton = {
                                StepButton(Icons.Filled.Remove, Strings.QUANTITY_MINUS, form.quantity > 1, "quantity-minus-$index") {
                                    change(form.copy(quantity = (form.quantity - 1).coerceAtLeast(1)))
                                }
                            },
                            endButton = {
                                StepButton(Icons.Filled.Add, Strings.QUANTITY_PLUS, form.quantity < PricingLimits.MAX_QUANTITY, "quantity-plus-$index") {
                                    change(form.copy(quantity = (form.quantity + 1).coerceAtMost(PricingLimits.MAX_QUANTITY)))
                                }
                            })
                    }
                    evaluation.pricing?.let { pricing ->
                        ArabicText(Strings.CALCULATION, style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RoundSpeaker(speakers.texts.calculation(pricing))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                ArabicText(ItemDescriber.calculation(pricing, script), style = MaterialTheme.typography.bodyLarge)
                                ArabicText(ArabicFormat.amount(pricing.total), style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    if (forms.size > 1) {
                        BigButton(Strings.DELETE, Icons.Filled.Delete, { dismissKeyboard(); onRemoveVariation(index) },
                            Modifier.testTag("remove-variation-$index"), kind = ButtonKind.Secondary)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // "Another size" only makes sense for a product that has a width and / or a height.
                    val takesSizes = forms.firstOrNull()?.visibleMeasurements.let { it != null && (it.width || it.height) }
                    if (pending.editingItemId == null && pending.product.formula.usesQuantity && takesSizes) {
                        BigButton(Strings.ADD_VARIATION, Icons.Filled.Add, { validateAnd(onAddVariation) },
                            Modifier.testTag("add-variation"), kind = ButtonKind.Accent)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (total != null) RoundSpeaker(speakers.texts.itemTotal(pending.product.name, total))
                        ArabicText("${Strings.TOTAL}: ${total?.let(ArabicFormat::amount) ?: "—"}",
                            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).testTag("entry-total"))
                    }
                    BigButton(Strings.CONFIRM, Icons.Filled.Check, { validateAnd(onSubmit) },
                        Modifier.testTag("confirm-item"), kind = ButtonKind.Success)
                    BigButton(Strings.CANCEL, Icons.Filled.Close, { dismissKeyboard(); onCancel() },
                        Modifier.testTag("cancel-item"), kind = ButtonKind.Secondary)
                }
            }
        }
    }
}

/** The "-" and "+" beside the quantity box: same height as the box, picture-style tile. */
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        StrictIconButton(onClick, description, Modifier.size(48.dp).testTag(tag), enabled = enabled) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f))
        }
    }
}

/**
 * A finished size shown small: its speaker reads the whole calculation, a tap anywhere else opens it again
 * (with its fields, speakers and delete button).
 */
@Composable
private fun CompactLine(index: Int, pricing: ItemPricing, onOpen: () -> Unit) {
    val speakers = LocalSpeakers.current
    val script = LocalUnitScript.current
    SectionCard(modifier = Modifier.testTag("variation-$index")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundSpeaker(speakers.texts.calculation(pricing))
            Row(
                Modifier.weight(1f).testTag("compact-$index").strictClickable(onClickLabel = Strings.EXPAND_LINE, onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    ArabicText("${Strings.LINE} ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    ArabicText(ItemDescriber.calculation(pricing, script), style = MaterialTheme.typography.bodyMedium)
                    ArabicText(ArabicFormat.amount(pricing.total), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
