package ma.workshop.invoice.domain.speech

/**
 * Words the app speaks, in Moroccan Darija spelling. They are written for the ear, not the screen:
 * the screens keep their own wording in `ui/Strings.kt`, the printed invoice in `ArabicTerms`.
 */
object DarijaTerms {
    // money and units
    const val DIRHAM = "درهم"
    const val DIRHAMS = "دراهم"
    const val TWO_DIRHAMS = "جوج دراهم"
    const val CENTIME = "سنتيم"
    const val METER = "متر"
    const val CENTIMETER = "سانتي"
    const val SQUARE = "مربع"
    const val POINT = "فاصلة"
    const val AND = "و"
    const val BY = "على"

    // labels
    const val TOTAL = "المجموع"
    const val INVOICE_TOTAL = "المجموع ديال الفاتورة"
    const val DEPOSIT = "التسبيق"
    const val REMAINING = "الباقي"
    const val NO_DEPOSIT = "ما كاين حتى تسبيق"
    const val FULLY_PAID = "الفاتورة خالصة"
    const val WIDTH = "العرض"
    const val HEIGHT = "الطول"
    const val QUANTITY = "العدد"
    const val PRICE = "الثمن"
    const val AREA = "المساحة"
    const val PERIMETER = "الدورة"
    const val CALCULATION = "الحساب"

    // rate bases ("500 درهم للمتر مربع")
    const val PER_SQUARE_METER = "للمتر مربع"
    const val PER_METER = "للمتر"
    const val PER_PIECE = "للحبة"

    // calculation methods
    const val METHOD_AREA = "بالمتر مربع"
    const val METHOD_PERIMETER = "بالمتر الطولي"
    const val METHOD_PIECE = "بالحبة"

    /** Played by "تجربة الصوت" in Settings. */
    const val VOICE_TEST = "هادا هو الصوت ديال التطبيق. ألف و ميتين و ستين درهم"
}
