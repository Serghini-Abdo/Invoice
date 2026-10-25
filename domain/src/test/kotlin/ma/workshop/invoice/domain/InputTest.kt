package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.input.DecimalParser
import ma.workshop.invoice.domain.input.KeypadEditor
import ma.workshop.invoice.domain.input.KeypadKey
import ma.workshop.invoice.domain.input.ParsedNumber
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class InputTest {

    private fun parse(text: String, decimals: Int = 3) = DecimalParser.parse(text, decimals)
    private fun value(text: String) = ParsedNumber.Value(BigDecimal(text))

    @Test
    fun `parses plain decimals with dot or comma`() {
        assertEquals(value("1.2"), parse("1.2"))
        assertEquals(value("1.2"), parse("1,2"))
        assertEquals(value("0.5"), parse(".5"))
        assertEquals(value("5"), parse("5."))
        assertEquals(value("1200"), parse("1 200"))
    }

    @Test
    fun `parses Arabic-Indic and Eastern Arabic-Indic digits`() {
        assertEquals(value("1.25"), parse("١٫٢٥"))
        assertEquals(value("1.25"), parse("۱٫۲۵"))
    }

    @Test
    fun `empty and whitespace are empty`() {
        assertEquals(ParsedNumber.Empty, parse(""))
        assertEquals(ParsedNumber.Empty, parse("   "))
    }

    @Test
    fun `rejects malformed text`() {
        for (bad in listOf("abc", "1.2.3", ".", "1e5", "+5", "1-2", "--1", "-", "12345678901234567890")) {
            assertEquals(ParsedNumber.Malformed, parse(bad), "should be malformed: $bad")
        }
    }

    @Test
    fun `reports negative numbers`() {
        assertEquals(ParsedNumber.Negative, parse("-1"))
        assertEquals(ParsedNumber.Negative, parse("−1.5"))
        assertEquals(ParsedNumber.Malformed, parse("-0")) // "-0" is not a meaningful quantity
    }

    @Test
    fun `never silently rounds extra decimals`() {
        assertEquals(ParsedNumber.TooManyDecimals, parse("1.2345", 3))
        assertEquals(value("1.2"), parse("1.2000", 3))
        assertEquals(ParsedNumber.TooManyDecimals, parse("1.234", 2))
    }

    @Test
    fun `keypad builds numbers digit by digit`() {
        var text = ""
        for (key in listOf(KeypadKey.Digit(1), KeypadKey.Decimal, KeypadKey.Digit(2), KeypadKey.Digit(0))) {
            text = KeypadEditor.apply(text, key, maxDecimals = 3)
        }
        assertEquals("1.20", text)
    }

    @Test
    fun `keypad decimal on empty starts with zero and only one decimal point is allowed`() {
        var text = KeypadEditor.apply("", KeypadKey.Decimal, 3)
        assertEquals("0.", text)
        text = KeypadEditor.apply(text, KeypadKey.Decimal, 3)
        assertEquals("0.", text)
        text = KeypadEditor.apply(text, KeypadKey.Digit(5), 3)
        assertEquals("0.5", text)
    }

    @Test
    fun `keypad ignores decimal point when no decimals are allowed`() {
        assertEquals("12", KeypadEditor.apply("12", KeypadKey.Decimal, maxDecimals = 0))
    }

    @Test
    fun `keypad limits decimals and integer digits`() {
        assertEquals("1.25", KeypadEditor.apply("1.25", KeypadKey.Digit(9), maxDecimals = 2))
        assertEquals("1234567", KeypadEditor.apply("1234567", KeypadKey.Digit(8), maxDecimals = 2))
    }

    @Test
    fun `keypad avoids leading zeros`() {
        assertEquals("5", KeypadEditor.apply("0", KeypadKey.Digit(5), 2))
        assertEquals("0", KeypadEditor.apply("0", KeypadKey.Digit(0), 2))
        assertEquals("0", KeypadEditor.apply("", KeypadKey.Digit(0), 2))
    }

    @Test
    fun `keypad backspace and clear`() {
        assertEquals("1.2", KeypadEditor.apply("1.25", KeypadKey.Backspace, 3))
        assertEquals("", KeypadEditor.apply("", KeypadKey.Backspace, 3))
        assertEquals("", KeypadEditor.apply("12.5", KeypadKey.Clear, 3))
    }
}
