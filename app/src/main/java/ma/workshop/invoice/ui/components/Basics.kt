package ma.workshop.invoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.theme.AppColors
import ma.workshop.invoice.ui.theme.TouchTarget

/** Top bar of every screen: a large back arrow and a clear title. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            StrictIconButton(onClick = onBack, contentDescription = Strings.BACK, modifier = Modifier.size(TouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        actions()
    }
}

enum class ButtonKind { Primary, Secondary, Success, Danger }

/**
 * The app's one button: always an icon AND a label (colour is never the only cue), at least 64dp tall.
 */
@Composable
fun BigButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    val buttonModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 64.dp)
    // Tighter than Material's 24dp so a two-word label fits beside its icon in a half-width button.
    val padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val disabledContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    when (kind) {
        ButtonKind.Secondary -> StrictButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            contentColor = if (enabled) MaterialTheme.colorScheme.primary else disabledContent,
            border = BorderStroke(2.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            content = content,
        )
        else -> {
            val container = when (kind) {
                ButtonKind.Success -> AppColors.Green
                ButtonKind.Danger -> AppColors.Red
                else -> MaterialTheme.colorScheme.primary
            }
            StrictButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                shape = shape,
                contentPadding = padding,
                containerColor = if (enabled) container else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                contentColor = if (enabled) Color.White else disabledContent,
                content = content,
            )
        }
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
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodyLarge)
                extra?.invoke()
            }
        },
        confirmButton = {
            StrictButton(
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                containerColor = if (danger) AppColors.Red else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(confirmText, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            StrictButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(dismissText, style = MaterialTheme.typography.labelLarge)
            }
        },
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
        title = { Text(Strings.VOICE_MISSING_TITLE, style = MaterialTheme.typography.titleLarge) },
        text = { Text(Strings.VOICE_MISSING_MESSAGE, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            StrictButton(
                onClick = onInstall,
                modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(Strings.AUDIO_INSTALL, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            StrictButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(Strings.LATER, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

enum class BannerKind { Info, Success, Warning, Error }

/** A message that always carries an icon and words, never colour alone. */
@Composable
fun StatusBanner(text: String, kind: BannerKind, modifier: Modifier = Modifier) {
    val (container, content, icon) = when (kind) {
        BannerKind.Info -> Triple(AppColors.SteelPale, AppColors.Steel, Icons.Filled.Info)
        BannerKind.Success -> Triple(AppColors.GreenPale, AppColors.Green, Icons.Filled.Check)
        BannerKind.Warning -> Triple(AppColors.AmberPale, AppColors.Amber, Icons.Filled.Warning)
        BannerKind.Error -> Triple(AppColors.RedPale, AppColors.Red, Icons.Filled.Warning)
    }
    Surface(modifier = modifier.fillMaxWidth(), color = container, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = content, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** A label on one side and an amount on the other. */
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
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = valueColor,
            textAlign = TextAlign.Start,
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

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Makes a whole row tappable with a proper role for accessibility. */
fun Modifier.tappable(onClick: () -> Unit): Modifier = this.strictClickable(onClick = onClick)

@Composable
fun Gap(height: Int) {
    Spacer(Modifier.height(height.dp))
}
