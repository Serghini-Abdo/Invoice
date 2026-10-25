package ma.workshop.invoice.domain.catalog

/** One filled and/or stroked shape of an illustration, as SVG path data in the 0..100 viewport. */
data class IllustrationPart(
    val pathData: String,
    /** ARGB, or null for no fill. */
    val fill: Long? = null,
    /** ARGB, or null for no stroke. */
    val stroke: Long? = null,
    val strokeWidth: Float = 0f,
    val evenOdd: Boolean = false,
)

data class Illustration(
    /** Stable key stored in the database and in invoice snapshots, e.g. "door". */
    val key: String,
    val nameAr: String,
    val parts: List<IllustrationPart>,
)

/**
 * The built-in product pictures. They are plain data (SVG path strings), so exactly the same artwork
 * is drawn by Compose on screen and by the Android Canvas in the PDF/PNG invoice, with no bitmap
 * assets to ship, scale or lose. Workshop owners pick one of these when creating a product.
 */
object Illustrations {

    const val VIEWPORT = 100

    /** Used when an illustration key is unknown (for example from a future version). */
    const val FALLBACK_KEY = "other"

    private const val DARK = 0xFF37474FL
    private const val STEEL = 0xFF607D8BL
    private const val STEEL_LIGHT = 0xFF90A4AEL
    private const val PALE = 0xFFCFD8DCL
    private const val GLASS = 0xFF81D4FAL
    private const val GOLD = 0xFFFFB300L
    private const val ORANGE = 0xFFEF6C00L
    private const val ORANGE_DARK = 0xFFBF360CL
    private const val SHADOW = 0x22000000L
    private const val SHINE = 0x66FFFFFFL

    val all: List<Illustration> = listOf(
        illustration("door", "باب") {
            fill(rect(14.0, 94.0, 72.0, 3.0), SHADOW)
            fill(rect(22.0, 4.0, 56.0, 91.0), DARK)
            fill(rect(27.0, 9.0, 46.0, 86.0), STEEL)
            fill(rect(33.0, 15.0, 34.0, 32.0), STEEL_LIGHT)
            stroke(rect(36.0, 18.0, 28.0, 26.0), DARK, 1.5f)
            fill(rect(33.0, 53.0, 34.0, 36.0), STEEL_LIGHT)
            stroke(rect(36.0, 56.0, 28.0, 30.0), DARK, 1.5f)
            fill(rect(70.0, 20.0, 5.0, 9.0), DARK)
            fill(rect(70.0, 48.0, 5.0, 9.0), DARK)
            fill(rect(70.0, 76.0, 5.0, 9.0), DARK)
            fill(rect(36.0, 49.0, 15.0, 2.5), 0xFFFF8F00L)
            fill(circle(38.0, 50.25, 4.5), GOLD)
        },
        illustration("window", "نافذة") {
            fill(rect(6.0, 86.0, 88.0, 6.0), STEEL_LIGHT)
            fill(rect(12.0, 10.0, 76.0, 76.0), 0xFFECEFF1L)
            stroke(rect(12.0, 10.0, 76.0, 76.0), 0xFF78909CL, 2f)
            fill(rect(17.0, 15.0, 66.0, 66.0), 0xFFB0BEC5L)
            for ((x, y) in listOf(20.0 to 18.0, 52.0 to 18.0, 20.0 to 51.0, 52.0 to 51.0)) {
                fill(rect(x, y, 28.0, 29.0), GLASS)
                fill("M${n(x + 4)} ${n(y + 24)}L${n(x + 13)} ${n(y + 4)}H${n(x + 19)}L${n(x + 10)} ${n(y + 24)}Z", SHINE)
            }
            fill(rect(48.0, 46.0, 4.0, 8.0), GOLD)
        },
        illustration("garage", "كراج") {
            fill(rect(0.0, 94.0, 100.0, 3.0), SHADOW)
            fill("M2 30L50 8L98 30Z", STEEL)
            fill(rect(10.0, 30.0, 80.0, 64.0), 0xFFFB8C00L)
            for (i in 1..5) {
                stroke("M10 ${n(30.0 + i * 10.5)}H90", ORANGE_DARK, 1.6f)
            }
            fill(rect(17.0, 33.0, 16.0, 6.0), 0xFFB3E5FCL)
            fill(rect(42.0, 33.0, 16.0, 6.0), 0xFFB3E5FCL)
            fill(rect(67.0, 33.0, 16.0, 6.0), 0xFFB3E5FCL)
            stroke(rect(10.0, 30.0, 80.0, 64.0), DARK, 3f)
            fill(rect(44.0, 84.0, 12.0, 2.5), DARK)
        },
        illustration("frame", "إطار") {
            stroke("M12 5H88M12 2V8M88 2V8", ORANGE, 1.6f)
            stroke("M5 14V90M2 14H8M2 90H8", ORANGE, 1.6f)
            fill("M14 14H88V90H14ZM26 26V78H76V26Z", STEEL, evenOdd = true)
            stroke("M26 26V78H76V26Z", STEEL_LIGHT, 1.5f)
            fill(rect(14.0, 14.0, 12.0, 12.0), DARK)
            fill(rect(76.0, 14.0, 12.0, 12.0), DARK)
            fill(rect(14.0, 78.0, 12.0, 12.0), DARK)
            fill(rect(76.0, 78.0, 12.0, 12.0), DARK)
        },
        illustration("hinges", "مفصلات") {
            fill(rect(12.0, 20.0, 32.0, 60.0), STEEL)
            fill(rect(56.0, 20.0, 32.0, 60.0), STEEL)
            for (y in listOf(32.0, 50.0, 68.0)) {
                fill(circle(24.0, y, 4.0), DARK)
                fill(circle(76.0, y, 4.0), DARK)
                fill(circle(24.0, y, 1.6), GOLD)
                fill(circle(76.0, y, 1.6), GOLD)
            }
            fill(rect(43.0, 12.0, 14.0, 76.0), STEEL_LIGHT)
            fill(rect(46.0, 12.0, 3.5, 76.0), SHINE)
            stroke("M43 38H57M43 62H57", DARK, 1.5f)
        },
        illustration("railing", "درابزين") {
            fill(rect(4.0, 20.0, 8.0, 74.0), DARK)
            fill(rect(88.0, 20.0, 8.0, 74.0), DARK)
            fill(circle(8.0, 17.0, 5.0), GOLD)
            fill(circle(92.0, 17.0, 5.0), GOLD)
            fill(rect(4.0, 30.0, 92.0, 8.0), DARK)
            fill(rect(4.0, 82.0, 92.0, 6.0), DARK)
            for (i in 0..6) {
                fill(rect(17.0 + i * 11.0, 38.0, 4.0, 44.0), STEEL)
            }
        },
        illustration("grille", "شباك حماية") {
            fill("M10 10H90V90H10ZM17 17V83H83V17Z", DARK, evenOdd = true)
            for (i in 0..4) {
                fill(rect(26.0 + i * 12.0, 17.0, 3.0, 66.0), STEEL)
            }
            for (y in listOf(36.0, 61.0)) {
                fill(rect(17.0, y, 66.0, 3.0), STEEL)
                for (i in 0..4) fill(circle(27.5 + i * 12.0, y + 1.5, 3.2), GOLD)
            }
        },
        illustration("gate", "بوابة") {
            fill(rect(0.0, 94.0, 100.0, 3.0), SHADOW)
            fill(rect(4.0, 14.0, 9.0, 82.0), DARK)
            fill(rect(87.0, 14.0, 9.0, 82.0), DARK)
            fill(circle(8.5, 11.0, 5.0), GOLD)
            fill(circle(91.5, 11.0, 5.0), GOLD)
            for (leafX in listOf(15.0, 51.0)) {
                stroke(rect(leafX, 26.0, 34.0, 64.0), STEEL, 3f)
                for (i in 0..3) {
                    val x = leafX + 6.0 + i * 7.5
                    fill(rect(x, 28.0, 2.2, 60.0), STEEL)
                    fill("M${n(x - 1)} 28L${n(x + 1.1)} 20L${n(x + 3.2)} 28Z", ORANGE)
                }
            }
            fill(rect(46.0, 54.0, 8.0, 10.0), GOLD)
        },
        illustration("shutter", "ستارة حديدية") {
            fill(rect(8.0, 6.0, 84.0, 16.0), DARK)
            fill(rect(12.0, 22.0, 76.0, 68.0), STEEL_LIGHT)
            for (i in 0..9) {
                stroke("M12 ${n(28.0 + i * 6.2)}H88", STEEL, 1.4f)
            }
            fill(rect(6.0, 22.0, 6.0, 74.0), DARK)
            fill(rect(88.0, 22.0, 6.0, 74.0), DARK)
            fill(rect(8.0, 90.0, 84.0, 6.0), DARK)
            fill(rect(44.0, 82.0, 12.0, 5.0), GOLD)
        },
        illustration("stairs", "درج") {
            fill("M8 92V76H28V60H48V44H68V28H88V92Z", STEEL)
            stroke("M8 76H28M28 60H48M48 44H68M68 28H88", GOLD, 3f)
            stroke("M8 70V56M28 54V40M48 38V24M68 22V8", DARK, 2.4f)
            stroke("M6 57L70 7", DARK, 3.2f)
        },
        illustration("fence", "سياج") {
            fill(rect(4.0, 40.0, 92.0, 5.0), DARK)
            fill(rect(4.0, 72.0, 92.0, 5.0), DARK)
            for (i in 0..8) {
                val x = 8.0 + i * 10.5
                fill(rect(x, 24.0, 5.0, 68.0), STEEL)
                fill("M${n(x - 0.5)} 24L${n(x + 2.5)} 12L${n(x + 5.5)} 24Z", ORANGE)
            }
        },
        illustration("canopy", "مظلة") {
            fill(rect(14.0, 40.0, 6.0, 54.0), DARK)
            fill(rect(78.0, 40.0, 6.0, 54.0), DARK)
            fill("M4 38L22 12H98L80 38Z", ORANGE)
            for (i in 1..5) {
                val t = i * 76.0 / 6.0
                stroke("M${n(4.0 + t)} 38L${n(22.0 + t)} 12", ORANGE_DARK, 1.6f)
            }
            fill(rect(4.0, 38.0, 76.0, 6.0), DARK)
            stroke("M20 44L30 56M78 44L68 56", DARK, 2.5f)
        },
        illustration("sheet", "صفيحة") {
            for (y in listOf(46.0, 22.0)) {
                fill("M14 ${n(y + 16)}L34 ${n(y)}H90L70 ${n(y + 16)}Z", PALE)
                fill(rect(14.0, y + 16, 56.0, 12.0), STEEL_LIGHT)
                fill("M70 ${n(y + 16)}L90 ${n(y)}V${n(y + 12)}L70 ${n(y + 28)}Z", STEEL)
                fill(circle(22.0, y + 22, 1.8), DARK)
                fill(circle(62.0, y + 22, 1.8), DARK)
            }
        },
        illustration("pipes", "أنابيب") {
            for ((cx, cy) in listOf(30.0 to 68.0, 62.0 to 68.0, 46.0 to 40.0)) {
                fill(circle(cx, cy, 15.0) + circle(cx, cy, 9.5), STEEL, evenOdd = true)
                stroke(circle(cx, cy, 15.0), DARK, 1.4f)
            }
        },
        illustration("lock", "قفل") {
            stroke("M34 46V32A16 16 0 0 1 66 32V46", DARK, 7f)
            fill(rect(24.0, 44.0, 52.0, 46.0), GOLD)
            stroke(rect(24.0, 44.0, 52.0, 46.0), 0xFFFF8F00L, 2f)
            fill(circle(50.0, 62.0, 5.5), DARK)
            fill("M47 64H53L55 78H45Z", DARK)
        },
        illustration(FALLBACK_KEY, "أخرى") {
            fill(
                "M50 14L77 29.5V60.5L50 76L23 60.5V29.5Z" + circle(50.0, 45.0, 12.0),
                STEEL_LIGHT,
                evenOdd = true,
            )
            stroke("M50 14L77 29.5V60.5L50 76L23 60.5V29.5Z", STEEL, 2.5f)
            fill(rect(44.0, 76.0, 12.0, 18.0), STEEL)
            stroke("M44 82L56 80M44 88L56 86", DARK, 1.5f)
        },
    )

    private val byKey: Map<String, Illustration> = all.associateBy { it.key }

    fun find(key: String): Illustration? = byKey[key]

    /** Never null: unknown keys (missing illustration) draw the generic picture instead of failing. */
    fun get(key: String): Illustration = byKey[key] ?: byKey.getValue(FALLBACK_KEY)

    // ---- tiny path-building helpers ----

    private class Builder {
        val parts = mutableListOf<IllustrationPart>()

        fun fill(path: String, color: Long, evenOdd: Boolean = false) {
            parts += IllustrationPart(path, fill = color, evenOdd = evenOdd)
        }

        fun stroke(path: String, color: Long, width: Float) {
            parts += IllustrationPart(path, stroke = color, strokeWidth = width)
        }
    }

    private fun illustration(key: String, nameAr: String, build: Builder.() -> Unit) =
        Illustration(key, nameAr, Builder().apply(build).parts)

    private fun n(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private fun rect(x: Double, y: Double, w: Double, h: Double) = "M${n(x)} ${n(y)}h${n(w)}v${n(h)}h${n(-w)}Z"

    private fun circle(cx: Double, cy: Double, r: Double) =
        "M${n(cx - r)} ${n(cy)}a${n(r)} ${n(r)} 0 1 0 ${n(2 * r)} 0a${n(r)} ${n(r)} 0 1 0 ${n(-2 * r)} 0Z"
}
