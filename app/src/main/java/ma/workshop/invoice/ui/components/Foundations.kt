package ma.workshop.invoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.theme.TouchTarget
import ma.workshop.invoice.ui.theme.appPalette

/**
 * Screen text. The layout is left-to-right, so plain [Text] would put an Arabic sentence on the left and
 * reorder strings that start with digits or Latin ("DH / م²", "0612 345 678 أحمد"). This one is always a
 * right-to-left paragraph, right-aligned inside the width it is given.
 */
@Composable
fun ArabicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Right,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        style = style.merge(TextStyle(textDirection = TextDirection.Rtl)),
    )
}

/** A warning line under a field: icon on the right, then the words, never colour alone. */
@Composable
internal fun FieldError(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        ArabicText(text, color = MaterialTheme.appPalette.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.appPalette.error, modifier = Modifier.size(20.dp))
    }
}

/**
 * The app's one text field: label above on the right (with an optional small hint), the box below, an
 * optional illustration beside it on the right, and an optional [trailing] control inside the box on the
 * left (the mic, or a clear button). Text is right-aligned; a phone number keeps its own digit order.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String? = null,
    illustration: ImageVector? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    error: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    tag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val palette = MaterialTheme.appPalette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (label != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (hint != null) {
                    ArabicText(hint, style = MaterialTheme.typography.bodyMedium, color = palette.grayStrong)
                    Spacer(Modifier.width(6.dp))
                }
                ArabicText(label, style = MaterialTheme.typography.titleSmall, color = palette.grayStrong)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (focused || error != null) 3.dp else 1.dp,
                    color = when {
                        error != null -> palette.error
                        focused -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    trailing?.invoke()
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = textStyle.merge(
                            TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Right, textDirection = TextDirection.Content),
                        ),
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        interactionSource = interaction,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                if (value.isEmpty() && placeholder != null) {
                                    ArabicText(placeholder, style = MaterialTheme.typography.bodyLarge, color = palette.gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                inner()
                            }
                        },
                    )
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = palette.grayStrong, modifier = Modifier.padding(end = 4.dp).size(26.dp))
                    }
                }
            }
            if (illustration != null) {
                Box(
                    Modifier
                        .size(width = 46.dp, height = 58.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(illustration, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (error != null) FieldError(error)
    }
}

/**
 * The top bar of every screen (left-to-right layout): the back arrow on the left, the title on the right
 * with an optional [speaker] just left of it. A screen with its own picture passes [centerIcon]; it sits in
 * the exact centre and the title is then limited to the right half, ending with "…" before the picture.
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    centerIcon: (@Composable () -> Unit)? = null,
    speaker: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
            ) {
                if (onBack != null) {
                    StrictIconButton(
                        onClick = onBack,
                        contentDescription = Strings.BACK,
                        modifier = Modifier.align(Alignment.CenterStart).size(TouchTarget),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
                if (centerIcon != null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                            .background(MaterialTheme.appPalette.surface, RoundedCornerShape(10.dp))
                            .testTag("top-bar-icon"),
                        contentAlignment = Alignment.Center,
                    ) { centerIcon() }
                }
                val titleMax = if (centerIcon != null) maxWidth / 2 - 26.dp else maxWidth - TouchTarget - 8.dp
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .widthIn(max = titleMax),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (speaker != null) speaker() else RoundSpeaker(title)
                    ArabicText(
                        text = title,
                        style = if (centerIcon != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 10.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
        }
    }
}

/** A slim line under the top bar for screen-wide controls (the mute chip), on the left like the back arrow. */
@Composable
fun AppSubBar(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * The speaker tile of a labelled button: it copies the button's colour, border, radius and height, sits on
 * the button's left, and is its own target (at least 48 × 48 dp). A tap only speaks.
 */
@Composable
fun SpeakerTile(
    kind: ButtonKind,
    onSpeak: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    buttonEnabled: Boolean = true,
    speechAvailable: Boolean = true,
) {
    val colors = buttonColors(kind, buttonEnabled, pressed = false)
    val shape = RoundedCornerShape(16.dp)
    StrictButton(
        onClick = onSpeak,
        onClickLabel = description,
        modifier = modifier
            .width(52.dp)
            .defaultMinSize(minHeight = 48.dp)
            .fillMaxHeight(),
        shape = shape,
        containerColor = colors.container,
        contentColor = colors.content,
        border = colors.border,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        contentDescription = description,
    ) {
        Icon(
            if (speechAvailable) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** `| speaker | label/icon |`: a [BigButton] with its [SpeakerTile] on the left, 4 dp apart. */
@Composable
fun SpokenButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    speechAvailable: Boolean = true,
    buttonModifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SpeakerTile(kind, onSpeak, "${Strings.SPEAK_LABEL}: $text", buttonEnabled = enabled, speechAvailable = speechAvailable)
        BigButton(text, icon, onClick, buttonModifier.weight(1f), kind = kind, enabled = enabled, spokenTile = false)
    }
}

/** The yellow "+ إضافة منتوج" button: dark text, a plus in a dark circle, a darker edge, and its speaker tile. */
@Composable
fun AddProductButton(onClick: () -> Unit, onSpeak: () -> Unit, speechAvailable: Boolean, modifier: Modifier = Modifier) {
    SpokenButton(
        text = Strings.ADD_PRODUCT,
        icon = Icons.Filled.Add,
        onClick = onClick,
        onSpeak = onSpeak,
        modifier = modifier,
        kind = ButtonKind.Accent,
        speechAvailable = speechAvailable,
        buttonModifier = Modifier.testTag("add-product"),
    )
}

/** A small picture beside a field, in the same soft green box for every field (width, height, price, quantity, customer). */
@Composable
fun PictureBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(width = 46.dp, height = 58.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun IconPicture(icon: ImageVector, modifier: Modifier = Modifier) {
    PictureBox(modifier) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
}

/** The dirham coin: a ring with "DH" in it (never a dollar sign). */
@Composable
fun CoinPicture(modifier: Modifier = Modifier) {
    PictureBox(modifier) {
        Box(
            Modifier
                .size(34.dp)
                .border(BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("DH", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
