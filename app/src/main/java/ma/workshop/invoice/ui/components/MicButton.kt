package ma.workshop.invoice.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

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

/** The phone's speech recognizer, provided once by AppNav. Null (previews, tests) means no mic buttons. */
val LocalDictation = staticCompositionLocalOf<Dictation?> { null }

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

    fun show(problem: DictationProblem, code: Int? = null) =
        Toast.makeText(context, problem.hint() + (code?.let { " ($it)" } ?: ""), Toast.LENGTH_LONG).show()

    fun start() {
        listening = true
        dictation.listen(onListening = {}) { outcome ->
            listening = false
            when (outcome) {
                is DictationOutcome.Text -> onText(DictationText.forField(outcome.text, maxLength))
                is DictationOutcome.Problem -> show(outcome.problem, outcome.code)
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
        val stop = {
            dictation.cancel()
            listening = false
        }
        AppDialog(
            title = Strings.DICTATE_LISTENING,
            message = "",
            onDismiss = stop,
            icon = Icons.Filled.Mic,
            buttons = listOf(DialogAction(Strings.CANCEL, Icons.Filled.Close, stop, ButtonKind.Secondary)),
        )
    }
}
