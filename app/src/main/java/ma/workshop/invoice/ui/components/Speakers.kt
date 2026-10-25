package ma.workshop.invoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.speech.DarijaSpeech
import ma.workshop.invoice.domain.speech.SpeechText
import ma.workshop.invoice.ui.Strings

/**
 * The "show speaker icons" switch of Settings, seen by every screen. When [show] is false (the default) no speaker
 * icon is drawn anywhere; when true, titles, labelled buttons and table rows get theirs. A tap only speaks.
 */
class SpeakerUi(
    val show: Boolean,
    val texts: SpeechText,
    val speak: (String) -> Unit,
) {
    fun hidden() = SpeakerUi(false, texts, speak)
}

val LocalSpeakers = staticCompositionLocalOf { SpeakerUi(false, DarijaSpeech) {} }

/** The round speaker beside a title or a table row (no label of its own); at least a 48 dp target. */
@Composable
fun RoundSpeaker(text: String, modifier: Modifier = Modifier) {
    val ui = LocalSpeakers.current
    if (!ui.show) return
    StrictIconButton(
        onClick = { ui.speak(text) },
        contentDescription = "${Strings.SPEAK_LABEL}: $text",
        modifier = modifier.size(48.dp).testTag("round-speaker"),
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
    }
}
