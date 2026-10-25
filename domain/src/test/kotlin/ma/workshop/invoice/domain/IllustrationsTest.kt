package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.catalog.Illustration
import ma.workshop.invoice.domain.catalog.Illustrations
import ma.workshop.invoice.domain.catalog.SeedCatalog
import ma.workshop.invoice.domain.pricing.PricingFormula
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IllustrationsTest {

    private val argCounts = mapOf(
        'M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7, 'Z' to 0,
    )

    /** Strict-enough SVG path syntax check: known commands, right argument counts, starts with a move. */
    private fun problems(path: String): List<String> {
        val tokens = Regex("""[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)""").findAll(path).map { it.value }.toList()
        val rebuilt = tokens.joinToString("")
        val stripped = path.filter { !it.isWhitespace() && it != ',' }
        val issues = mutableListOf<String>()
        if (rebuilt != stripped) issues += "unparseable characters in: $path"
        if (tokens.isEmpty() || tokens.first().lowercase() != "m") issues += "must start with a move: $path"

        var i = 0
        while (i < tokens.size) {
            val command = tokens[i].single()
            if (!command.isLetter()) {
                issues += "number without a command near token $i in $path"
                break
            }
            val expected = argCounts.getValue(command.uppercaseChar())
            var j = i + 1
            while (j < tokens.size && !tokens[j][0].isLetter()) j++
            val count = j - i - 1
            if (expected == 0) {
                if (count != 0) issues += "'$command' takes no arguments in $path"
            } else if (count == 0 || count % expected != 0) {
                issues += "'$command' has $count numbers, expected a multiple of $expected in $path"
            }
            i = j
        }
        return issues
    }

    @Test
    fun `keys are unique and the fallback exists`() {
        assertEquals(Illustrations.all.size, Illustrations.all.map { it.key }.toSet().size)
        assertNotNull(Illustrations.find(Illustrations.FALLBACK_KEY))
        assertEquals(Illustrations.FALLBACK_KEY, Illustrations.get("no-such-picture").key)
    }

    @Test
    fun `every illustration path is valid SVG path data and every part draws something`() {
        for (illustration in Illustrations.all) {
            assertTrue(illustration.parts.isNotEmpty(), illustration.key)
            for (part in illustration.parts) {
                assertEquals(emptyList(), problems(part.pathData), "illustration ${illustration.key}")
                assertTrue(part.fill != null || part.stroke != null, "${illustration.key} part is invisible")
                if (part.stroke != null) assertTrue(part.strokeWidth > 0f, "${illustration.key} stroke width")
            }
            assertTrue(illustration.nameAr.isNotBlank())
        }
    }

    @Test
    fun `the seed catalog uses real illustrations and has no prices`() {
        val seeds = SeedCatalog.products(now = 1)
        assertTrue(seeds.size >= 5)
        for (seed in seeds) assertNotNull(Illustrations.find(seed.illustration), seed.name)

        val byName = seeds.associateBy { it.name }
        assertEquals(PricingFormula.AREA, byName.getValue("باب حديد").formula)
        assertEquals(PricingFormula.AREA, byName.getValue("نافذة ألمنيوم").formula)
        assertEquals(PricingFormula.AREA, byName.getValue("باب الكراج").formula)
        assertEquals(PricingFormula.PERIMETER, byName.getValue("إطار حديد").formula)
        assertEquals(PricingFormula.PER_ITEM, byName.getValue("مفصلات").formula)
        assertEquals(seeds.map { it.sortOrder }, seeds.indices.toList())
        assertTrue(seeds.all { it.isActive })
    }

    /** Writes a contact sheet so the artwork can be looked at (build/illustrations.html). */
    @Test
    fun `render contact sheet`() {
        fun css(argb: Long): String {
            val a = ((argb shr 24) and 0xFF) / 255.0
            return "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF},$a)"
        }

        fun svg(illustration: Illustration): String = buildString {
            append("""<svg viewBox="0 0 100 100" width="200" height="200">""")
            for (part in illustration.parts) {
                append("""<path d="${part.pathData}" """)
                append("""fill="${part.fill?.let(::css) ?: "none"}" """)
                if (part.stroke != null) append("""stroke="${css(part.stroke!!)}" stroke-width="${part.strokeWidth}" """)
                if (part.evenOdd) append("""fill-rule="evenodd" """)
                append("/>")
            }
            append("</svg>")
        }

        val html = buildString {
            append("<html dir='rtl'><body style='margin:0;font-family:sans-serif;background:#fff'>")
            append("<div style='display:grid;grid-template-columns:repeat(4,220px);gap:12px;padding:12px'>")
            for (illustration in Illustrations.all) {
                append("<div style='border:1px solid #ddd;border-radius:12px;padding:8px;text-align:center'>")
                append(svg(illustration))
                append("<div style='font-size:20px'>${illustration.nameAr} (${illustration.key})</div></div>")
            }
            append("</div></body></html>")
        }
        val out = File("build/illustrations.html")
        out.parentFile.mkdirs()
        out.writeText(html)
        assertTrue(out.length() > 1000)
    }
}
