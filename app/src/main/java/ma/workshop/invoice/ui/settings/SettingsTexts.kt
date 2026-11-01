package ma.workshop.invoice.ui.settings

import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.ExportFormat
import ma.workshop.invoice.domain.settings.SpeechLanguage
import ma.workshop.invoice.domain.settings.SpeechRate
import ma.workshop.invoice.domain.settings.UnitLabelScript
import ma.workshop.invoice.ui.Strings

/** The one-line current value shown on each Settings category card. */

fun productsValue(activeProducts: Int): String = "$activeProducts ${Strings.PRODUCT_WORD}"

fun workshopValue(settings: AppSettings): String = settings.workshopName.trim().ifEmpty { Strings.NOT_WRITTEN }

fun formatValue(settings: AppSettings): String = when (settings.exportFormat) {
    ExportFormat.PNG -> Strings.FORMAT_PNG
    ExportFormat.PDF -> Strings.FORMAT_PDF
    ExportFormat.BOTH -> Strings.FORMAT_BOTH
}

/** For example "م · سم / م", or "m · cm / m" (isolated, so the Latin letters keep their order). */
fun unitsValue(settings: AppSettings): String {
    val script = settings.unitLabelScript
    val labels = if (script == UnitLabelScript.ARABIC) Strings.UNIT_LABELS_ARABIC else Strings.UNIT_LABELS_LATIN
    return ArabicFormat.isolate("${ArabicFormat.unit(settings.lengthUnit, script)} · $labels", script)
}

fun speechLanguageLabel(language: SpeechLanguage): String = when (language) {
    SpeechLanguage.DARIJA -> Strings.SPEECH_LANGUAGE_DARIJA
    SpeechLanguage.STANDARD -> Strings.SPEECH_LANGUAGE_STANDARD
}

fun speechRateLabel(rate: SpeechRate): String = when (rate) {
    SpeechRate.SLOW -> Strings.SPEECH_RATE_SLOW
    SpeechRate.NORMAL -> Strings.SPEECH_RATE_NORMAL
    SpeechRate.FAST -> Strings.SPEECH_RATE_FAST
}

fun audioValue(settings: AppSettings): String =
    if (!settings.audioEnabled) Strings.AUDIO_VALUE_OFF
    else "${Strings.AUDIO_VALUE_ON} · ${speechLanguageLabel(settings.speechLanguage)} · ${speechRateLabel(settings.speechRate)}"
