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

/** Accent is the yellow of "+ إضافة منتوج": dark text and a darker edge, never white on it. */
enum class ButtonKind { Primary, Secondary, Success, Danger, Accent }

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
        ButtonKind.Accent -> ButtonColors(
            if (pressed) palette.balancePressed else palette.balance,
            palette.onBalance,
            BorderStroke(2.dp, palette.balanceBorder),
        )
    }
}

/**
 * The app's one button: always an icon AND a label (colour is never the only cue), at least 64dp tall.
 * The icon sits on the right of the label, in Arabic reading order ("→ متابعة"), and a forward arrow
 * points right. Pressed, the button darkens (yellow) and shrinks a little, so the press is visible.
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
            SpeakerTile(kind, { speakers.speak(spokenText ?: text) }, "${Strings.SPEAK_LABEL}: $text", buttonEnabled = enabled)
            BigButton(text, icon, onClick, Modifier.weight(1f), kind, enabled, spokenTile = false)
        }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = buttonColors(kind, enabled, pressed)
    val content: @Composable RowScope.() -> Unit = {
        ArabicText(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            if (kind == ButtonKind.Accent) {
                // The plus in a dark circle, yellow on dark: the plus never sits as a thin glyph on yellow.
                Box(Modifier.size(38.dp).background(colors.content, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = colors.container, modifier = Modifier.size(28.dp))
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

/** The words, then the icon on their right: the small buttons of dialogs. */
@Composable
private fun DialogButton(text: String, icon: ImageVector, onClick: () -> Unit, container: Color = Color.Transparent, content: Color = MaterialTheme.colorScheme.primary) {
    StrictButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 56.dp),
        containerColor = container,
        contentColor = content,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ArabicText(text, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null)
    }
}

/** A confirmation dialog with icon + text on both actions, so no decision depends on colour. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = Strings.CONFIRM,
    dismissText: String = Strings.CANCEL,
    danger: Boolean = false,
    extra: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                RoundSpeaker(title)
                ArabicText(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                if (message.isNotEmpty()) ArabicText(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                extra?.invoke()
            }
        },
        // Left-to-right: cancel on the left, confirm on the right ("| إلغاء | تأكيد |").
        confirmButton = {
            DialogButton(
                confirmText,
                Icons.Filled.Check,
                onConfirm,
                container = if (danger) MaterialTheme.appPalette.error else MaterialTheme.colorScheme.primary,
                content = MaterialTheme.appPalette.onColor,
            )
        },
        dismissButton = { DialogButton(dismissText, Icons.Filled.Close, onDismiss) },
    )
}

/**
 * Shown the first time a speaker is tapped on a phone without an offline Arabic voice. Nothing is lost
 * by closing it: every figure stays on screen.
 */
@Composable
fun VoiceMissingDialog(onInstall: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, modifier = Modifier.size(36.dp)) },
        title = { ArabicText(Strings.VOICE_MISSING_TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth()) },
        text = { ArabicText(Strings.VOICE_MISSING_MESSAGE, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            DialogButton(
                Strings.AUDIO_INSTALL,
                Icons.Filled.Download,
                onInstall,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.appPalette.onColor,
            )
        },
        dismissButton = { DialogButton(Strings.LATER, Icons.Filled.Close, onDismiss) },
    )
}

enum class BannerKind { Info, Success, Warning, Error }

/** A message that always carries an icon and words, never colour alone. The icon is on the right. */
@Composable
fun StatusBanner(text: String, kind: BannerKind, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.appPalette
    val (container, content, icon) = when (kind) {
        BannerKind.Info -> Triple(palette.infoContainer, palette.info, Icons.Filled.Info)
        BannerKind.Success -> Triple(palette.paidContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Filled.Check)
        BannerKind.Warning -> Triple(palette.warningContainer, palette.onWarningContainer, Icons.Filled.Warning)
        BannerKind.Error -> Triple(palette.errorContainer, palette.onErrorContainer, Icons.Filled.Warning)
    }
    Surface(modifier = modifier.fillMaxWidth(), color = container, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ArabicText(text, color = content, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
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
            value,
            style = if (emphasized) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = valueColor,
        )
        Spacer(Modifier.width(8.dp))
        ArabicText(
            label,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) { content() }
    }
}

/** Makes a whole row tappable with a proper role for accessibility. */
fun Modifier.tappable(onClick: () -> Unit): Modifier = this.strictClickable(onClick = onClick)

@Composable
fun Gap(height: Int) {
    Spacer(Modifier.height(height.dp))
}
