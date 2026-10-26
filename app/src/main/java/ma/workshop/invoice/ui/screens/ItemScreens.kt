package ma.workshop.invoice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.flow.PendingItem
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.format.ItemDescriber
import ma.workshop.invoice.domain.input.KeypadEditor
import ma.workshop.invoice.domain.input.KeypadKey
import ma.workshop.invoice.domain.pricing.Field
import ma.workshop.invoice.domain.pricing.ItemEntryForm
import ma.workshop.invoice.domain.pricing.PricingLimits
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.arabic
import ma.workshop.invoice.ui.components.AudioToggle
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.FieldBox
import ma.workshop.invoice.ui.components.IllustrationImage
import ma.workshop.invoice.ui.components.NumericKeypad
import ma.workshop.invoice.ui.components.QuantityStepper
import ma.workshop.invoice.ui.components.AppSubBar
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.SpeakerButton
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.components.CoinPicture
import ma.workshop.invoice.ui.components.FieldIcons
import ma.workshop.invoice.ui.components.FitText
import ma.workshop.invoice.ui.components.IconPicture
import ma.workshop.invoice.ui.components.RoundSpeaker
import ma.workshop.invoice.ui.components.LocalSpeakers
import ma.workshop.invoice.ui.components.LocalUnitScript
import ma.workshop.invoice.ui.rateLabel
import ma.workshop.invoice.ui.rateUnit

/** Which control the on-screen keypad is currently typing into. */
private enum class ActiveField { WIDTH, HEIGHT, RATE, QUANTITY }

/**
 * Step 3: enter the details of the selected product.
 *
 * Only the fields the product's pricing formula needs are shown. The total is recalculated on every
 * keystroke. Errors appear only after the user presses continue, never while still typing.
 */
@Composable
fun ItemEntryScreen(
    pending: PendingItem,
    onFormChange: (ItemEntryForm) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val form = pending.form
    val formula = form.formula
    val script = LocalUnitScript.current
    val needsDimensions = formula.needsDimensions
    val order = if (needsDimensions) {
        listOf(ActiveField.WIDTH, ActiveField.HEIGHT, ActiveField.RATE, ActiveField.QUANTITY)
    } else {
        listOf(ActiveField.RATE, ActiveField.QUANTITY)
    }

    var active by rememberSaveable { mutableStateOf(order.first()) }
    // The first digit typed after selecting a field replaces what is there.
    var fresh by rememberSaveable { mutableStateOf(true) }
    var quantityText by rememberSaveable { mutableStateOf(form.quantity.toString()) }

    fun select(field: ActiveField) {
        active = field
        fresh = true
        if (field == ActiveField.QUANTITY) quantityText = form.quantity.toString()
    }

    fun onKey(key: KeypadKey) {
        fun base(current: String) = if (fresh && key is KeypadKey.Digit) "" else if (fresh && key == KeypadKey.Decimal) "" else current
        when (active) {
            ActiveField.WIDTH -> {
                val next = KeypadEditor.apply(base(form.widthText), key, PricingLimits.MAX_DIMENSION_DECIMALS)
                onFormChange(form.copy(widthText = next))
            }
            ActiveField.HEIGHT -> {
                val next = KeypadEditor.apply(base(form.heightText), key, PricingLimits.MAX_DIMENSION_DECIMALS)
                onFormChange(form.copy(heightText = next))
            }
            ActiveField.RATE -> {
                val next = KeypadEditor.apply(base(form.rateText), key, PricingLimits.MAX_RATE_DECIMALS)
                onFormChange(form.copy(rateText = next))
            }
            ActiveField.QUANTITY -> {
                quantityText = KeypadEditor.apply(base(quantityText), key, maxDecimals = 0, maxIntegerDigits = 4)
                onFormChange(form.copy(quantity = quantityText.toIntOrNull() ?: 0))
            }
        }
        fresh = false
    }

    fun next() {
        val index = order.indexOf(active)
        if (index < order.lastIndex) select(order[index + 1]) else onSubmit()
    }

    val evaluation = form.evaluate()
    val show = pending.showErrors
    fun errorFor(field: Field): String? = if (show) evaluation.issueFor(field)?.arabic() else null

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(
            title = pending.product.name,
            onBack = onBack,
            centerIcon = { IllustrationImage(pending.product.illustration, Modifier.size(36.dp)) },
        )

        // live total + continue, always visible: the button on the left, the total on the right
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigButton(
                    text = Strings.CONTINUE,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = onSubmit,
                    modifier = Modifier.width(if (LocalSpeakers.current.show) 190.dp else 170.dp).testTag("entry-continue"),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    ArabicText(Strings.ITEM_TOTAL, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    FitText(
                        evaluation.pricing?.let { ArabicFormat.amount(it.total) } ?: "-",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    evaluation.pricing?.let { ItemDescriber.baseAmount(it, script) }?.let {
                        ArabicText(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (show && !evaluation.isValid) StatusBanner(Strings.FILL_REQUIRED, BannerKind.Warning)

            if (needsDimensions) {
                FieldBox(
                    label = Strings.WIDTH,
                    tag = "field-width",
                    value = form.widthText,
                    selected = active == ActiveField.WIDTH,
                    onClick = { select(ActiveField.WIDTH) },
                    error = errorFor(Field.WIDTH),
                    trailing = { UnitLabel(form.widthUnit) },
                    picture = { IconPicture(FieldIcons.Width) },
                )
                FieldBox(
                    label = Strings.HEIGHT,
                    tag = "field-height",
                    value = form.heightText,
                    selected = active == ActiveField.HEIGHT,
                    onClick = { select(ActiveField.HEIGHT) },
                    error = errorFor(Field.HEIGHT),
                    trailing = { UnitLabel(form.heightUnit) },
                    picture = { IconPicture(FieldIcons.Height) },
                )
            }
            FieldBox(
                label = formula.rateLabel(),
                tag = "field-rate",
                value = form.rateText,
                selected = active == ActiveField.RATE,
                onClick = { select(ActiveField.RATE) },
                error = errorFor(Field.RATE),
                picture = { CoinPicture() },
                trailing = {
                    ArabicText(formula.rateUnit(script), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
            QuantityStepper(
                quantity = form.quantity,
                text = if (active == ActiveField.QUANTITY && !fresh) quantityText else form.quantity.toString(),
                selected = active == ActiveField.QUANTITY,
                onMinus = {
                    val q = (form.quantity - 1).coerceAtLeast(1)
                    quantityText = q.toString()
                    onFormChange(form.copy(quantity = q))
                },
                onPlus = {
                    val q = (form.quantity + 1).coerceAtMost(PricingLimits.MAX_QUANTITY)
                    quantityText = q.toString()
                    onFormChange(form.copy(quantity = q))
                },
                onSelect = { select(ActiveField.QUANTITY) },
                maxQuantity = PricingLimits.MAX_QUANTITY,
                error = errorFor(Field.QUANTITY),
                picture = { IconPicture(Icons.Filled.Inventory2) },
            )
        }

        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            // Plain lambdas on purpose, never `::onKey` / `::next`: Compose treats two references to the same
            // local function as equal, so it would keep calling the first one, which still holds the
            // original (empty) form, and every key press would wipe the other fields.
            NumericKeypad(
                onKey = { key -> onKey(key) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                allowDecimal = active != ActiveField.QUANTITY,
                onNext = { next() },
                nextLabel = if (active == order.last()) Strings.CONTINUE else Strings.NEXT,
            )
        }
    }
}

/** Step 4: the calculation breakdown with confirm and cancel. */
@Composable
fun ItemConfirmScreen(
    pending: PendingItem,
    speechAvailable: Boolean,
    audioEnabled: Boolean,
    onToggleAudio: () -> Unit,
    onSpeakTotal: () -> Unit,
    onReplay: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val pricing = pending.confirmedPricing
    // Speak the total once when the screen appears (the caller decides whether audio is enabled).
    LaunchedEffect(pricing) { if (pricing != null) onSpeakTotal() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(
            title = Strings.CONFIRM_ITEM_TITLE,
            onBack = onBack,
            centerIcon = { IllustrationImage(pending.product.illustration, Modifier.size(36.dp)) },
        )
        AppSubBar { AudioToggle(audioEnabled, onToggleAudio) }
        if (pricing == null) return@Column

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The picture is in the top bar now; the name stays large, on the right.
            ArabicText(pending.product.name, style = MaterialTheme.typography.headlineMedium)

            // The total comes first: it is what the owner came here to check (and hear), so it never
            // needs scrolling to find. The step-by-step calculation follows below it.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpeakerButton(onClick = onReplay, available = speechAvailable, description = Strings.REPLAY)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        ArabicText(Strings.ITEM_TOTAL, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        ArabicText(
                            ArabicFormat.amount(pricing.total),
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        RoundSpeaker(LocalSpeakers.current.texts.calculation(pricing))
                        ArabicText(Strings.CALCULATION, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (line in ItemDescriber.breakdown(pricing, LocalUnitScript.current)) {
                        Column(horizontalAlignment = Alignment.End) {
                            ArabicText(line.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ArabicText(line.value, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            if (LocalSpeakers.current.show) {
                // With the speaker tiles two buttons no longer fit side by side: stacked, each full width.
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(Strings.CONFIRM, Icons.Filled.Check, onConfirm, Modifier.fillMaxWidth().testTag("confirm-item"), kind = ButtonKind.Success)
                    BigButton(Strings.CANCEL, Icons.Filled.Close, onCancel, Modifier.fillMaxWidth().testTag("cancel-item"), kind = ButtonKind.Secondary)
                }
            } else {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(Strings.CANCEL, Icons.Filled.Close, onCancel, Modifier.weight(1f), kind = ButtonKind.Secondary)
                    BigButton(Strings.CONFIRM, Icons.Filled.Check, onConfirm, Modifier.weight(1f), kind = ButtonKind.Success)
                }
            }
        }
    }
}

/** The unit written beside a dimension, where the m / cm switch used to be. It follows Settings. */
@Composable
private fun UnitLabel(unit: ma.workshop.invoice.domain.pricing.LengthUnit) {
    ArabicText(
        ArabicFormat.unit(unit, LocalUnitScript.current),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}
