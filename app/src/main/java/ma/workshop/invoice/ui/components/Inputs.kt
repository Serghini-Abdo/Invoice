package ma.workshop.invoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.input.KeypadKey
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.theme.AppColors
import ma.workshop.invoice.ui.theme.TouchTarget

/** "1.2" typed internally is shown the Moroccan way, with a comma. */
fun displayNumber(text: String): String = text.replace('.', ',')

/**
 * Scrolls the enclosing scrollable so this element is visible whenever [selected] becomes true. On a small
 * phone the keypad leaves room for only some of the fields, and the one being typed into must never hide.
 */
@Composable
private fun Modifier.bringIntoViewWhen(selected: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) requester.bringIntoView() }
    return this.bringIntoViewRequester(requester)
}

private val KeyHeight = 50.dp
private val KeyGap = 6.dp

@Composable
private fun KeyCap(
    modifier: Modifier,
    tag: String,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .testTag(tag)
            .strictClickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Large on-screen numeric keypad, four rows high so the fields above stay visible on small phones:
 *
 *     7 8 9 | backspace
 *     4 5 6 | next
 *     1 2 3 | next
 *     , 0 C | next
 *
 * The digits keep the usual phone order (1-2-3 from the left) even in the right-to-left app.
 */
@Composable
fun NumericKeypad(
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true,
    onNext: (() -> Unit)? = null,
    nextLabel: String = Strings.NEXT,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                for (row in listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                        for (digit in row) {
                            KeyCap(Modifier.weight(1f).height(KeyHeight), "key-$digit", { onKey(KeypadKey.Digit(digit)) }, digit.toString()) {
                                Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                    if (allowDecimal) {
                        KeyCap(Modifier.weight(1f).height(KeyHeight), "key-decimal", { onKey(KeypadKey.Decimal) }, ",") {
                            Text(",", style = MaterialTheme.typography.headlineMedium)
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(KeyHeight))
                    }
                    KeyCap(Modifier.weight(1f).height(KeyHeight), "key-0", { onKey(KeypadKey.Digit(0)) }, "0") {
                        Text("0", style = MaterialTheme.typography.headlineMedium)
                    }
                    KeyCap(Modifier.weight(1f).height(KeyHeight), "key-clear", { onKey(KeypadKey.Clear) }, Strings.CLEAR) {
                        Text(Strings.CLEAR, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                KeyCap(Modifier.fillMaxWidth().height(KeyHeight), "key-backspace", { onKey(KeypadKey.Backspace) }, Strings.BACKSPACE) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = Strings.BACKSPACE, modifier = Modifier.size(28.dp))
                }
                if (onNext != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KeyHeight * 3 + KeyGap * 2)
                            .testTag("key-next")
                            .strictClickable(role = Role.Button, onClickLabel = nextLabel, onClick = onNext),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(nextLabel, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** A big value box. The selected one has a thick border AND a caret, so selection never relies on colour. */
@Composable
fun FieldBox(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    error: String? = null,
    tag: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = (if (tag != null) modifier.testTag(tag) else modifier).bringIntoViewWhen(selected),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .strictClickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (selected || error != null) 3.dp else 1.dp,
                color = when {
                    error != null -> AppColors.Red
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (value.isEmpty()) placeholder else displayNumber(value),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (value.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        if (selected) {
                            Box(
                                Modifier
                                    .padding(start = 2.dp)
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
                trailing?.invoke()
            }
        }
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.Red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(error, color = AppColors.Red, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Metres / centimetres switch shown beside every dimension. */
@Composable
fun UnitToggle(selected: LengthUnit, onSelect: (LengthUnit) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (unit in LengthUnit.entries) {
            val isSelected = unit == selected
            val label = if (unit == LengthUnit.METER) Strings.METER_UNIT else Strings.CM_UNIT
            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .strictClickable(role = Role.RadioButton, onClickLabel = label) { onSelect(unit) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(50.dp)
            .strictClickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        contentColor = androidx.compose.ui.graphics.Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * Quantity with large minus/plus buttons. The number itself can also be tapped to type a value with
 * the keypad. It always starts at 1 and never goes below 1.
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    text: String,
    selected: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSelect: () -> Unit,
    maxQuantity: Int,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    Column(modifier.bringIntoViewWhen(selected), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Strings.QUANTITY, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundAction(Icons.Filled.Remove, Strings.DECREASE, enabled = quantity > 1, onClick = onMinus)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .strictClickable(role = Role.Button, onClickLabel = Strings.QUANTITY, onClick = onSelect),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (selected || error != null) 3.dp else 1.dp,
                    color = when {
                        error != null -> AppColors.Red
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = text.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            RoundAction(Icons.Filled.Add, Strings.INCREASE, enabled = quantity < maxQuantity, onClick = onPlus)
        }
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.Red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(error, color = AppColors.Red, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Speaker target. Separate from the card's selection target so a tap here never selects the product. */
@Composable
fun SpeakerButton(onClick: () -> Unit, available: Boolean, modifier: Modifier = Modifier, description: String = Strings.SPEAK_NAME) {
    StrictIconButton(onClick = onClick, contentDescription = description, modifier = modifier.size(TouchTarget)) {
        Icon(
            imageVector = if (available) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
            contentDescription = description,
            tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * Catalog card: the picture is the main thing, the Arabic name sits under it, and the speaker is a
 * separate, always-bottom-corner target. Tapping the picture/name selects; tapping the speaker only speaks.
 */
@Composable
fun ProductCard(
    name: String,
    illustration: String,
    onSelect: () -> Unit,
    onSpeak: () -> Unit,
    speechAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .strictClickable(role = Role.Button, onClickLabel = name, onClick = onSelect)
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IllustrationImage(illustration, Modifier.size(112.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .heightIn(min = 60.dp)
                        .padding(top = 6.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.End) {
                SpeakerButton(onClick = onSpeak, available = speechAvailable)
            }
        }
    }
}

/** Quick mute switch for screens that speak. Icon and word both change, so it never relies on colour. */
@Composable
fun AudioToggle(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("audio-toggle")
            .strictClickable(role = Role.Switch, onClickLabel = if (enabled) Strings.MUTE else Strings.AUDIO_ENABLED, onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (enabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (enabled) Strings.SOUND_ON else Strings.SOUND_OFF, style = MaterialTheme.typography.titleSmall)
        }
    }
}
