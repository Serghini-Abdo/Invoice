package ma.workshop.invoice.domain.input

/** Keys of the large on-screen numeric keypad. */
sealed interface KeypadKey {
    data class Digit(val digit: Int) : KeypadKey {
        init {
            require(digit in 0..9) { "digit must be 0..9" }
        }
    }

    data object Decimal : KeypadKey
    data object Backspace : KeypadKey
    data object Clear : KeypadKey
}

/**
 * Pure text-editing rules for the numeric keypad, so the Compose keypad stays a thin view.
 * The text always uses `.` internally; the UI may display it with a comma.
 */
object KeypadEditor {

    const val MAX_INTEGER_DIGITS = 7

    fun apply(
        text: String,
        key: KeypadKey,
        maxDecimals: Int,
        maxIntegerDigits: Int = MAX_INTEGER_DIGITS,
    ): String = when (key) {
        is KeypadKey.Digit -> appendDigit(text, key.digit, maxDecimals, maxIntegerDigits)
        KeypadKey.Decimal -> when {
            maxDecimals <= 0 -> text
            text.contains('.') -> text
            text.isEmpty() -> "0."
            else -> "$text."
        }
        KeypadKey.Backspace -> text.dropLast(1)
        KeypadKey.Clear -> ""
    }

    private fun appendDigit(text: String, digit: Int, maxDecimals: Int, maxIntegerDigits: Int): String {
        val dot = text.indexOf('.')
        if (dot >= 0) {
            val decimals = text.length - dot - 1
            return if (decimals < maxDecimals) text + digit else text
        }
        return when {
            text == "0" -> if (digit == 0) text else digit.toString()
            text.length >= maxIntegerDigits -> text
            else -> text + digit
        }
    }
}
