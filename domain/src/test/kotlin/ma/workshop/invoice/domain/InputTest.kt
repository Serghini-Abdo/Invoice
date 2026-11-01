package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.input.DecimalParser
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

}
