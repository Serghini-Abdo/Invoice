package ma.workshop.invoice.domain.speech

import ma.workshop.invoice.domain.invoice.PaymentSummary
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.settings.SpeechLanguage

/**
 * Everything the app can say, in one spoken language. Callers never build sentences themselves: they
 * pick the implementation for the language chosen in Settings with [SpeechTexts.of].
 */
interface SpeechText {
    fun amount(money: Money): String
    fun quantity(quantity: Int): String

    /** A measurement in the unit it was typed in. */
    fun length(length: Length): String

    /** The unit price with its basis: "500 درهم للمتر مربع". */
    fun rate(pricing: ItemPricing): String

    /** One invoice row: name, size, quantity and line total. */
    fun itemLine(productName: String, pricing: ItemPricing): String

    /** Spoken on the confirmation screen. */
    fun itemTotal(productName: String, total: Money): String

    /** The whole calculation card as one sentence, method first. */
    fun calculation(pricing: ItemPricing): String

    /** Name of a calculation method. */
    fun method(formula: PricingFormula): String

    /** The total + deposit + balance card as one sentence. */
    fun paymentSummary(payment: PaymentSummary): String

    /** Played by "تجربة الصوت" in Settings. */
    val voiceTest: String
}

object SpeechTexts {
    fun of(language: SpeechLanguage): SpeechText = when (language) {
        SpeechLanguage.DARIJA -> DarijaSpeech
        SpeechLanguage.STANDARD -> StandardSpeech
    }
}
