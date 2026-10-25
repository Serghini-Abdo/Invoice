package ma.workshop.invoice.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ma.workshop.invoice.domain.speech.DictationProblem
import ma.workshop.invoice.domain.speech.DictationText
import ma.workshop.invoice.platform.Dictation
import ma.workshop.invoice.platform.DictationOutcome
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.theme.TouchTarget

/** The hint shown for each way speak-to-fill can fail. Typing always remains possible. */
fun DictationProblem.hint(): String = when (this) {
    DictationProblem.NOT_AVAILABLE -> Strings.DICTATE_NOT_AVAILABLE
    DictationProblem.OFFLINE_PACK_MISSING -> Strings.DICTATE_OFFLINE_MISSING
    DictationProblem.NO_MATCH -> Strings.DICTATE_NO_MATCH
    DictationProblem.PERMISSION_DENIED -> Strings.DICTATE_PERMISSION_DENIED
    DictationProblem.BUSY -> Strings.DICTATE_BUSY
    DictationProblem.FAILED -> Strings.DICTATE_FAILED
}

/**
 * Speak-to-fill for a text-only field (customer name, product name; never a number). The microphone
 * permission is requested on the first tap only, after a short explanation. The recognised text is
 * handed to [onText] as plain text, trimmed to [maxLength], so the field stays fully editable.
 */
@Composable
fun MicButton(dictation: Dictation, maxLength: Int, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var explaining by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    fun show(problem: DictationProblem) = Toast.makeText(context, problem.hint(), Toast.LENGTH_LONG).show()

    fun start() {
        listening = true
        dictation.checkOfflinePack { installed -> if (installed == false) show(DictationProblem.OFFLINE_PACK_MISSING) }
        dictation.listen(onListening = {}) { outcome ->
            listening = false
            when (outcome) {
                is DictationOutcome.Text -> onText(DictationText.forField(outcome.text, maxLength))
                is DictationOutcome.Problem -> show(outcome.problem)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else show(DictationProblem.PERMISSION_DENIED)
    }

    DisposableEffect(Unit) { onDispose { dictation.cancel() } }

    StrictIconButton(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            when {
                granted -> start()
                !dictation.permissionAsked -> explaining = true
                else -> show(DictationProblem.PERMISSION_DENIED)
            }
        },
        contentDescription = Strings.DICTATE,
        modifier = modifier.size(TouchTarget).testTag("mic"),
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(28.dp))
    }

    if (explaining) {
        ConfirmDialog(
            title = Strings.DICTATE_PERMISSION_TITLE,
            message = Strings.DICTATE_PERMISSION_MESSAGE,
            onConfirm = {
                explaining = false
                dictation.permissionAsked = true
                permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { explaining = false },
            confirmText = Strings.CONTINUE,
            dismissText = Strings.DICTATE_TYPE_INSTEAD,
        )
    }

    if (listening) {
        AlertDialog(
            onDismissRequest = {
                dictation.cancel()
                listening = false
            },
            icon = { Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
            title = { ArabicText(Strings.DICTATE_LISTENING, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) },
            confirmButton = {
                StrictButton(
                    onClick = {
                        dictation.cancel()
                        listening = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    ArabicText(Strings.CANCEL, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            },
        )
    }
}
