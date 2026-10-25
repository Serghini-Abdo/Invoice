package ma.workshop.invoice.domain.catalog

/**
 * A product or invoice-item picture can be a photo from the phone instead of a built-in illustration. It is
 * stored in the same `illustration` text as "photo:<file name>", so no database change is needed; the file
 * itself lives in the app's private photo folder. Every invoice item owns a literal copy of the file.
 */
object PhotoKey {
    const val PREFIX = "photo:"

    private val FILE_NAME = Regex("[A-Za-z0-9_-]{1,64}\\.jpg")
    private val IN_TEXT = Regex("photo:([A-Za-z0-9_-]{1,64}\\.jpg)")

    fun isPhoto(key: String): Boolean = fileName(key) != null

    /** The file name inside the photo folder, or null when [key] is not a (valid) photo key. */
    fun fileName(key: String): String? =
        if (key.startsWith(PREFIX)) key.removePrefix(PREFIX).takeIf { FILE_NAME.matches(it) } else null

    fun keyFor(fileName: String): String = PREFIX + fileName

    /** Every photo file name mentioned anywhere in [texts] (database columns, draft and snapshot JSON). */
    fun referencedIn(texts: Iterable<String>): Set<String> =
        texts.flatMap { text -> IN_TEXT.findAll(text).map { it.groupValues[1] }.toList() }.toSet()
}
