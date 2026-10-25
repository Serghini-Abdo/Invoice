package ma.workshop.invoice.platform.drive

import android.accounts.Account
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
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.DriveFailureMapper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface DriveAuthResult {
    data class Token(val accessToken: String, val accountEmail: String? = null) : DriveAuthResult

    /** The user must approve access once; launch [pendingIntent] and pass the result back. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult

    /** [detail] is the raw code (for example "api 10"), shown small so a helper can look it up. */
    data class Failed(val failure: BackupFailure, val detail: String) : DriveAuthResult
}

/** [detail] carries the raw status, e.g. "HTTP 403 accessNotConfigured". */
class DriveException(val failure: BackupFailure, val detail: String) : Exception("$failure: $detail")

class RemoteBackup(val id: String, val name: String, val modifiedAt: Long, val size: Long?)

/** What the backup service needs from Google Drive; faked in the instrumented worker tests. */
interface DriveBackupApi {
    /** Silent when the user already agreed once; otherwise [DriveAuthResult.NeedsConsent]. Never shows UI itself. */
    suspend fun authorize(useSavedAccount: Boolean): DriveAuthResult

    /** Reads the token from the result of the consent screen; null if the user declined. */
    fun tokenFromConsent(data: Intent?): DriveAuthResult.Token?

    suspend fun list(token: String): List<RemoteBackup>

    /** Creates a new file and returns it (with the size Drive stored, so the caller can verify it). */
    suspend fun create(token: String, name: String, json: String): RemoteBackup

    suspend fun delete(token: String, id: String)

    /** The newest backup's content, or null if the user has none yet. */
    suspend fun downloadNewest(token: String): String?
}

/**
 * Backs up to, and restores from, the app's *private* Google Drive folder (`appDataFolder`).
 *
 * - Scope: only `drive.appdata`, the narrowest Drive scope. The app cannot see any of the user's other
 *   Drive files, and no Google verification review is needed for it.
 * - Authorization: Google's Identity AuthorizationClient. The OAuth client is identified by package name +
 *   signing certificate registered in Google Cloud Console; no secret or client id is in the source (see
 *   docs/google-drive-setup.md). After the first consent, `authorize` returns a token without any UI, which
 *   is what lets a background worker back up unattended.
 * - Plain REST over HttpURLConnection, so no heavy Google API client library is shipped.
 * - Every failure is classified into a [BackupFailure] so the screen can say what really went wrong.
 *
 * Invoicing never touches this class.
 */
class DriveBackupClient(
    private val context: Context,
    private val savedAccount: () -> String?,
    private val saveAccount: (String) -> Unit,
) : DriveBackupApi {

    override suspend fun authorize(useSavedAccount: Boolean): DriveAuthResult {
        val playStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (playStatus != ConnectionResult.SUCCESS) {
            return DriveAuthResult.Failed(BackupFailure.NO_PLAY_SERVICES, "play services $playStatus")
        }
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_APPDATA)))
        if (useSavedAccount) savedAccount()?.let { builder.setAccount(Account(it, "com.google")) }
        return try {
            val result = Identity.getAuthorizationClient(context).authorize(builder.build()).awaitTask()
            val pending = result.pendingIntent
            val token = result.accessToken
            when {
                result.hasResolution() && pending != null -> DriveAuthResult.NeedsConsent(pending)
                token != null -> {
                    val email = runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()
                    if (email != null) saveAccount(email)
                    DriveAuthResult.Token(token, email)
                }
                else -> DriveAuthResult.Failed(BackupFailure.DRIVE_UNKNOWN, "no token")
            }
        } catch (e: ApiException) {
            DriveAuthResult.Failed(DriveFailureMapper.fromPlayStatus(e.statusCode), "api ${e.statusCode}")
        } catch (e: IOException) {
            DriveAuthResult.Failed(BackupFailure.NO_NETWORK, e.javaClass.simpleName)
        } catch (e: Exception) {
            DriveAuthResult.Failed(BackupFailure.DRIVE_UNKNOWN, e.javaClass.simpleName + ": " + e.message.orEmpty())
        }
    }

    override fun tokenFromConsent(data: Intent?): DriveAuthResult.Token? {
        if (data == null) return null
        return try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            val token = result.accessToken ?: return null
            val email = runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()
            if (email != null) saveAccount(email)
            DriveAuthResult.Token(token, email)
        } catch (e: ApiException) {
            null
        }
    }

    override suspend fun list(token: String): List<RemoteBackup> = io {
        val query = URLEncoder.encode("trashed=false", "UTF-8")
        val order = URLEncoder.encode("modifiedTime desc", "UTF-8")
        val fields = URLEncoder.encode("files(id,name,modifiedTime,size)", "UTF-8")
        val connection = open("$API/files?spaces=appDataFolder&q=$query&orderBy=$order&pageSize=100&fields=$fields", token, "GET")
        val files = JSONObject(readBody(connection)).optJSONArray("files") ?: return@io emptyList()
        (0 until files.length()).map { parse(files.getJSONObject(it)) }
    }

    override suspend fun create(token: String, name: String, json: String): RemoteBackup = io {
        val boundary = "workshop-invoice-${System.nanoTime()}"
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("mimeType", "application/json")
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json)
            append("\r\n--$boundary--")
        }.toByteArray(Charsets.UTF_8)

        val fields = URLEncoder.encode("id,name,modifiedTime,size", "UTF-8")
        val connection = open("$UPLOAD/files?uploadType=multipart&fields=$fields", token, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        parse(JSONObject(readBody(connection)))
    }

    override suspend fun delete(token: String, id: String) {
        io { readBody(open("$API/files/$id", token, "DELETE")) }
    }

    override suspend fun downloadNewest(token: String): String? {
        val newest = list(token).firstOrNull() ?: return null
        return io { readBody(open("$API/files/${newest.id}?alt=media", token, "GET")) }
    }

    // ---- REST ----

    private fun parse(file: JSONObject) = RemoteBackup(
        id = file.getString("id"),
        name = file.optString("name"),
        modifiedAt = runCatching { Instant.parse(file.optString("modifiedTime")).toEpochMilli() }.getOrDefault(0L),
        size = file.optString("size").toLongOrNull(),
    )

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
                val reason = errorReason(connection)
                throw DriveException(DriveFailureMapper.fromHttp(code, reason), "HTTP $code" + (reason?.let { " $it" } ?: ""))
            }
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                    if (buffer.size() > MAX_BYTES) throw DriveException(BackupFailure.DATA_INVALID, "backup too large")
                }
            }
            return buffer.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    /** The first `reason` of Google's JSON error body (for example accessNotConfigured), if readable. */
    private fun errorReason(connection: HttpURLConnection): String? = try {
        val text = connection.errorStream?.use { it.readBytes().take(16 * 1024).toByteArray().toString(Charsets.UTF_8) }
        text?.let { JSONObject(it).optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason") }?.ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: DriveException) {
            throw e
        } catch (e: IOException) {
            throw DriveException(BackupFailure.NO_NETWORK, e.javaClass.simpleName)
        }
    }

    companion object {
        const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
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
