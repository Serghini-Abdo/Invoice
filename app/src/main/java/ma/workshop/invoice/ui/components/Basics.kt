package ma.workshop.invoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.theme.appPalette

/** Tonal is the light blue "+ إضافة منتوج" button: blue text and edge on a pale blue fill. */
enum class ButtonKind { Primary, Secondary, Success, Danger, Tonal }

internal class ButtonColors(val container: Color, val content: Color, val border: BorderStroke?)

/** Colours of a button and of its speaker tile, so the two always match. */
@Composable
internal fun buttonColors(kind: ButtonKind, enabled: Boolean, pressed: Boolean): ButtonColors {
    val palette = MaterialTheme.appPalette
    if (!enabled) return ButtonColors(palette.infoContainer, palette.gray, null)
    return when (kind) {
        ButtonKind.Primary, ButtonKind.Success -> ButtonColors(MaterialTheme.colorScheme.primary, palette.onColor, null)
        ButtonKind.Danger -> ButtonColors(palette.error, palette.onColor, null)
        ButtonKind.Secondary -> ButtonColors(MaterialTheme.colorScheme.surface, palette.ink, BorderStroke(2.dp, palette.gray))
        ButtonKind.Tonal -> ButtonColors(
            if (pressed) palette.tonalPressed else MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The app's one button: a label (colour is never the only cue) and, when it carries a meaning, an icon
 * after the label in Arabic reading order (on its left). Navigation buttons such as "متابعة" have no icon:
 * the only arrow in the app is the back arrow in the top bar. At least 64dp tall. Pressed, the button
 * darkens and shrinks a little, so the press is visible.
 */
@Composable
fun BigButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    /** With "show speaker icons" on, a tile of this button's style sits on its left and speaks the label. */
    spokenTile: Boolean = true,
    /** What the tile says when it is not the label itself (the disabled "continue" explains why). */
    spokenText: String? = null,
) {
    val speakers = LocalSpeakers.current
    if (spokenTile && speakers.show) {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BigButton(text, icon, onClick, Modifier.weight(1f), kind, enabled, spokenTile = false)
            SpeakerTile(kind, { speakers.speak(spokenText ?: text) }, "${Strings.SPEAK_LABEL}: $text", buttonEnabled = enabled)
        }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = buttonColors(kind, enabled, pressed)
    val content: @Composable RowScope.() -> Unit = {
        FitText(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            if (kind == ButtonKind.Tonal) {
                // A white plus in a blue circle, so it reads as an add button at a glance.
                Box(Modifier.size(38.dp).background(colors.content, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.appPalette.onColor, modifier = Modifier.size(28.dp))
                }
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
    StrictButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .graphicsLayer {
                val scale = if (pressed && enabled) 0.97f else 1f
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        // Tighter than Material's 24dp so a two-word label fits beside its icon in a half-width button.
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        containerColor = colors.container,
        contentColor = colors.content,
        border = colors.border,
        interactionSource = interaction,
        content = content,
    )
}

/** One button of an [AppDialog]: always words and an icon, so no decision depends on colour. */
class DialogAction(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val kind: ButtonKind = ButtonKind.Primary,
)

/**
 * The app's one dialog: Material's window, but the content reads from the right and the buttons are
 * full-width app buttons stacked inside it, the main action on top (each has its speaker when speakers are on).
 * The title's speaker reads the title and the message as one sentence.
 */
@Composable
fun AppDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    buttons: List<DialogAction>,
    modifier: Modifier = Modifier,
    spokenText: String = if (message.isEmpty()) title else "$title. $message",
    icon: ImageVector? = null,
    extra: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(36.dp)) } },
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                ArabicText(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                RoundSpeaker(spokenText)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.Start) {
                if (message.isNotEmpty()) ArabicText(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                extra?.invoke()
                buttons.forEach { action -> BigButton(action.text, action.icon, action.onClick, kind = action.kind) }
            }
        },
        confirmButton = {},
    )
}

/** A confirmation dialog: the main action on top, "cancel" below it. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = Strings.CONFIRM,
    dismissText: String = Strings.CANCEL,
    danger: Boolean = false,
    confirmIcon: ImageVector = Icons.Filled.Check,
    dismissIcon: ImageVector = Icons.Filled.Close,
    extra: @Composable (() -> Unit)? = null,
) {
    AppDialog(
        title = title,
        message = message,
        onDismiss = onDismiss,
        extra = extra,
        buttons = listOf(
            DialogAction(confirmText, confirmIcon, onConfirm, if (danger) ButtonKind.Danger else ButtonKind.Primary),
            DialogAction(dismissText, dismissIcon, onDismiss, ButtonKind.Secondary),
        ),
    )
}

/**
 * Shown the first time a speaker is tapped on a phone without an offline Arabic voice. Nothing is lost
 * by closing it: every figure stays on screen.
 */
@Composable
fun VoiceMissingDialog(onInstall: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = Strings.VOICE_MISSING_TITLE,
        message = Strings.VOICE_MISSING_MESSAGE,
        onDismiss = onDismiss,
        icon = Icons.AutoMirrored.Filled.VolumeOff,
        buttons = listOf(
            DialogAction(Strings.AUDIO_INSTALL, Icons.Filled.Download, onInstall),
            DialogAction(Strings.LATER, Icons.Filled.Close, onDismiss, ButtonKind.Secondary),
        ),
    )
}

/** [Success] is blue (an action worked); [Paid] is the only green banner and means "fully paid". */
enum class BannerKind { Info, Success, Paid, Warning, Error }

/** A message that always carries an icon and words, never colour alone. The icon is on the right, first. */
@Composable
fun StatusBanner(text: String, kind: BannerKind, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.appPalette
    val (container, content, icon) = when (kind) {
        BannerKind.Info -> Triple(palette.infoContainer, palette.info, Icons.Filled.Info)
        BannerKind.Success -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Filled.Check)
        BannerKind.Paid -> Triple(palette.paidContainer, palette.onPaidContainer, Icons.Filled.Check)
        BannerKind.Warning -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Filled.Warning)
        BannerKind.Error -> Triple(palette.errorContainer, palette.onErrorContainer, Icons.Filled.Warning)
    }
    Surface(modifier = modifier.fillMaxWidth(), color = container, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            ArabicText(text, color = content, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
    }
}

/** The label on the right and the amount on the left, as on the printed invoice. */
@Composable
fun AmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArabicText(
            label,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ArabicText(
            value,
            style = if (emphasized) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = valueColor,
        )
    }
}

/** Round icon badge used on the home screen. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Int = 72) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

/** A white card; its content lines up on the right. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) { content() }
    }
}

/** Makes a whole row tappable with a proper role for accessibility. */
fun Modifier.tappable(onClick: () -> Unit): Modifier = this.strictClickable(onClick = onClick)

@Composable
fun Gap(height: Int) {
    Spacer(Modifier.height(height.dp))
}
