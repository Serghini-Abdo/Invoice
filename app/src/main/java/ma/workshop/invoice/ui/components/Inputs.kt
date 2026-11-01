package ma.workshop.invoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.theme.TouchTarget

/** Speaker target. Separate from the card's selection target so a tap here never selects the product. */
@Composable
fun SpeakerButton(onClick: () -> Unit, available: Boolean, modifier: Modifier = Modifier, description: String = Strings.SPEAK_NAME) {
    if (!LocalSpeakers.current.show) return
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
                ArabicText(
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
            // Bottom-left, inside the card, its own target (End is the left edge in the right-to-left layout).
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.End) {
                SpeakerButton(onClick = onSpeak, available = speechAvailable)
            }
        }
    }
}

