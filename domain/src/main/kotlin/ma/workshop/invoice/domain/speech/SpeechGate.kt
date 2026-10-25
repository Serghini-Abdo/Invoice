package ma.workshop.invoice.domain.speech

/** What the phone's text-to-speech can do right now. */
enum class VoiceState { CHECKING, READY, MISSING }

/** What a speaker tap should do. */
enum class SpeechDecision {
    /** Speak, replacing anything still being spoken. */
    SPEAK,

    /** Audio is switched off: stay silent; the screen already shows everything. */
    AUDIO_OFF,

    /** No offline Arabic voice: explain how to download one. Returned once per app run. */
    ASK_FOR_VOICE,

    /** No voice, and the explanation was already shown: stay silent. */
    NO_VOICE,

    /** The voice check has not finished yet. */
    STILL_CHECKING,
}

/**
 * Decides what a speaker tap does. A tap never selects or navigates whatever the answer is; this only
 * chooses between speaking, staying silent and (once) explaining that a voice must be downloaded.
 */
class SpeechGate {
    private var askedForVoice = false

    fun decide(audioEnabled: Boolean, voice: VoiceState): SpeechDecision = when {
        !audioEnabled -> SpeechDecision.AUDIO_OFF
        voice == VoiceState.READY -> SpeechDecision.SPEAK
        voice == VoiceState.CHECKING -> SpeechDecision.STILL_CHECKING
        askedForVoice -> SpeechDecision.NO_VOICE
        else -> {
            askedForVoice = true
            SpeechDecision.ASK_FOR_VOICE
        }
    }
}
