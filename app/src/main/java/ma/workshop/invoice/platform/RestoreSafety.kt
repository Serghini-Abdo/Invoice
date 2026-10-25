package ma.workshop.invoice.platform

import java.io.File

/**
 * Recovery support for restores.
 *
 * Applying a backup is one database transaction, so an interruption can never leave half-restored
 * data: SQLite rolls the whole thing back. On top of that:
 *  - a full copy of the current data is written to a file *before* any restore, so the user can always
 *    go back to what they had;
 *  - a marker file exists while a restore is running, so if the process died mid-restore the app can
 *    tell the user that nothing changed instead of leaving them wondering.
 */
class RestoreSafety(private val directory: File) {

    private val marker get() = File(directory, "restore.in-progress")

    fun writeSafetyCopy(json: String, now: Long): File {
        directory.mkdirs()
        val file = File(directory, "before-restore-$now.json")
        // Write to a temporary name first so a half-written file is never mistaken for a good copy.
        val temp = File(directory, "${file.name}.tmp")
        temp.writeText(json, Charsets.UTF_8)
        check(temp.renameTo(file)) { "could not finalize safety copy" }
        prune(keep = 3)
        return file
    }

    fun begin() {
        directory.mkdirs()
        marker.writeText("1")
    }

    fun finish() {
        marker.delete()
    }

    /** True once if a previous restore did not finish. The marker is cleared so the notice shows once. */
    fun consumeInterrupted(): Boolean {
        val interrupted = marker.exists()
        if (interrupted) marker.delete()
        return interrupted
    }

    private fun prune(keep: Int) {
        val copies = directory.listFiles { f -> f.name.startsWith("before-restore-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            .orEmpty()
        copies.drop(keep).forEach { it.delete() }
    }
}
