package ma.workshop.invoice.platform

import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.speech.SpeechDecision
import ma.workshop.invoice.domain.speech.SpeechGate
import ma.workshop.invoice.domain.speech.VoiceState

/** What happened when a speaker was tapped. None of these ever selects or navigates. */
enum class SpeakResult { SPOKEN, AUDIO_OFF, ASK_FOR_VOICE, UNAVAILABLE, CHECKING }

/**
 * Every speaker in the app goes through here: it applies the audio switch and the speech rate from the
 * settings, and asks (once per app run) for an Arabic voice to be downloaded when none is installed.
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

    /** Speech the app starts by itself (a total on arrival): never asks for a voice, never interrupts the user. */
    fun sayIfReady(text: String, settings: AppSettings) {
        if (settings.audioEnabled && controller.voiceState == VoiceState.READY) controller.speak(text, settings.speechRate)
    }

    fun stop() = controller.stop()
}
