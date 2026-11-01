package ma.workshop.invoice.platform

import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.speech.SpeechDecision
import ma.workshop.invoice.domain.speech.SpeechGate

/** What happened when a speaker was tapped. None of these ever selects or navigates. */
enum class SpeakResult { SPOKEN, AUDIO_OFF, ASK_FOR_VOICE, UNAVAILABLE, CHECKING }

/**
 * Every speaker in the app goes through here: it applies the audio switch and the speech rate from the
 * settings, and asks (once per app run) for an Arabic voice to be downloaded when none is installed.
 * Nothing is spoken automatically (owner decision, 2026-11-01): the only callers are speaker taps.
 */
class Speaker(private val controller: SpeechController) {
    private val gate = SpeechGate()

    /** A speaker tap. */
    fun say(text: String, settings: AppSettings): SpeakResult {
        controller.ensureStarted()
        return when (gate.decide(settings.audioEnabled, controller.voiceState)) {
            SpeechDecision.SPEAK -> {
                controller.speak(text, settings.speechRate)
                SpeakResult.SPOKEN
            }
            SpeechDecision.AUDIO_OFF -> SpeakResult.AUDIO_OFF
            SpeechDecision.ASK_FOR_VOICE -> SpeakResult.ASK_FOR_VOICE
            SpeechDecision.NO_VOICE -> SpeakResult.UNAVAILABLE
            SpeechDecision.STILL_CHECKING -> SpeakResult.CHECKING
        }
    }

    fun stop() = controller.stop()
}
