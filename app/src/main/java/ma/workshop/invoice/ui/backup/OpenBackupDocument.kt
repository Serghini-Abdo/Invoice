package ma.workshop.invoice.ui.backup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import ma.workshop.invoice.platform.backup.AppFolderBackupStore

/** The normal "open a file" screen, but starting in the Fawatir Warsha folder where backups are saved. */
class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, AppFolderBackupStore.pickerStartUri())
        }
        return intent
    }
}
