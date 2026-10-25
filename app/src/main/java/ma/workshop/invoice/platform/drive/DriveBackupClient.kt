package ma.workshop.invoice.platform.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface DriveAuthResult {
    data class Token(val accessToken: String) : DriveAuthResult

    /** The user must approve access once; launch [pendingIntent] and pass the result back. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult

    data object PlayServicesMissing : DriveAuthResult
    data class Failed(val reason: String) : DriveAuthResult
}

class DriveException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NETWORK, AUTH, NOT_FOUND, SERVER, TOO_LARGE }
}

/**
 * Backs up to, and restores from, the app's *private* Google Drive folder (`appDataFolder`).
 *
 * - Scope: only `drive.appdata`, the narrowest Drive scope. The app cannot see any of the user's other
 *   Drive files, and no Google verification review is needed for it.
 * - Authorization: Google's Identity AuthorizationClient (the current replacement for the deprecated
 *   GoogleSignIn). The OAuth client is identified by package name + signing certificate registered in
 *   Google Cloud Console; no secret or client id is in the source (see docs/google-drive-setup.md).
 * - Plain REST over HttpURLConnection, so no heavy Google API client library is shipped.
 * - A backup never overwrites the previous one in place: the new file is created first and older ones
 *   are removed only after that succeeded, so an interrupted upload cannot leave the user with none.
 *
 * Invoicing never touches this class; it is only used from the backup screen.
 */
class DriveBackupClient(private val context: Context) {

    suspend fun authorize(): DriveAuthResult {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            return DriveAuthResult.PlayServicesMissing
        }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_APPDATA)))
            .build()
        return try {
            val result = Identity.getAuthorizationClient(context).authorize(request).awaitTask()
            val pending = result.pendingIntent
            val token = result.accessToken
            when {
                result.hasResolution() && pending != null -> DriveAuthResult.NeedsConsent(pending)
                token != null -> DriveAuthResult.Token(token)
                else -> DriveAuthResult.Failed("no token")
            }
        } catch (e: ApiException) {
            DriveAuthResult.Failed("api ${e.statusCode}")
        } catch (e: Exception) {
            DriveAuthResult.Failed(e.message.orEmpty())
        }
    }

    /** Reads the access token from the result of the consent screen; null if the user declined. */
    fun tokenFromConsent(data: Intent?): String? {
        if (data == null) return null
        return try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        } catch (e: ApiException) {
            null
        }
    }

    /** Stores [json] as the current backup. */
    suspend fun upload(token: String, json: String) {
        io {
            val existing = listBackupIds(token)
            val newId = createFile(token, json)
            // Only now that the new copy exists are the older ones removed (best effort).
            for (id in existing) if (id != newId) runCatching { deleteFile(token, id) }
        }
    }

    /** The newest backup's content, or null if the user has none yet. */
    suspend fun download(token: String): String? = io {
        val id = listBackupIds(token).firstOrNull() ?: return@io null
        val connection = open("$API/files/$id?alt=media", token, "GET")
        readBody(connection)
    }

    // ---- REST ----

    private fun listBackupIds(token: String): List<String> {
        val query = URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8")
        val order = URLEncoder.encode("modifiedTime desc", "UTF-8")
        val connection = open("$API/files?spaces=appDataFolder&q=$query&orderBy=$order&pageSize=20&fields=files(id)", token, "GET")
        val files = JSONObject(readBody(connection)).optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { files.getJSONObject(it).getString("id") }
    }

    private fun createFile(token: String, json: String): String {
        val boundary = "workshop-invoice-${System.nanoTime()}"
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("mimeType", "application/json")
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json)
            append("\r\n--$boundary--")
        }.toByteArray(Charsets.UTF_8)

        val connection = open("$UPLOAD/files?uploadType=multipart&fields=id", token, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        return JSONObject(readBody(connection)).getString("id")
    }

    private fun deleteFile(token: String, id: String) {
        val connection = open("$API/files/$id", token, "DELETE")
        readBody(connection)
    }

    private fun open(url: String, token: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        return connection
    }

    private fun readBody(connection: HttpURLConnection): String {
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw DriveException(
                    when (code) {
                        401, 403 -> DriveException.Kind.AUTH
                        404 -> DriveException.Kind.NOT_FOUND
                        else -> DriveException.Kind.SERVER
                    },
                    "HTTP $code",
                )
            }
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                    if (buffer.size() > MAX_BYTES) throw DriveException(DriveException.Kind.TOO_LARGE, "backup too large")
                }
            }
            return buffer.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: DriveException) {
            throw e
        } catch (e: IOException) {
            throw DriveException(DriveException.Kind.NETWORK, e.message.orEmpty())
        }
    }

    companion object {
        const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE_NAME = "workshop-invoice-backup.json"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val MAX_BYTES = 64 * 1024 * 1024
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
