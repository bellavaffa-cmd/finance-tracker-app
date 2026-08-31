package com.financetracker.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.financetracker.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionName: String, val downloadUrl: String, val notes: String)

data class UpdateUiState(
    val checking: Boolean = false,
    val available: UpdateInfo? = null,
    val downloading: Boolean = false,
    /** 0f..1f once the download's total size is known; null while unknown or idle. */
    val downloadProgress: Float? = null,
    /** Brief handoff to the system package installer, after the download finishes. */
    val installing: Boolean = false,
    val message: String? = null,
    val dismissed: Boolean = false
)

/**
 * Over-the-air updates for the GitHub-distributed build: checks the repo's latest Release and, if it
 * names a newer version than [BuildConfig.VERSION_NAME], downloads its APK asset and hands it to the
 * system package installer.
 *
 * This is the one part of the app that talks to the network, and it is worth being precise about
 * what that means: it contacts api.github.com and the release asset URL, sends no data beyond an
 * ordinary HTTP GET, and never touches the ledger. Nothing financial leaves the device.
 *
 * The installer will only treat the download as an *update* if it is signed with the same key as
 * the installed app. These builds are debug-signed, so that key is the local Android debug key -
 * every published APK must be built on the same machine or the install turns into "app not
 * installed".
 */
class UpdateManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    // No response cache: a release check served from cache would keep reporting the old version
    // straight after a release, and the APK is far too large to want cached anyway.
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Check GitHub for a newer release. [silent] suppresses the "up to date" / error status text. */
    fun checkForUpdate(silent: Boolean = true) {
        scope.launch {
            _state.value = _state.value.copy(checking = true, message = null)
            _state.value = try {
                val info = withContext(Dispatchers.IO) { fetchLatest() }
                if (info != null && isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                    _state.value.copy(checking = false, available = info, dismissed = false, message = null)
                } else {
                    _state.value.copy(
                        checking = false,
                        available = null,
                        message = if (silent) null else "You're on the latest version (${BuildConfig.VERSION_NAME})."
                    )
                }
            } catch (e: Exception) {
                _state.value.copy(
                    checking = false,
                    message = if (silent) null else "Update check failed: ${e.message}"
                )
            }
        }
    }

    fun dismiss() {
        _state.value = _state.value.copy(dismissed = true)
    }

    /** Download the available update's APK and launch the installer. */
    fun startUpdate() {
        val info = _state.value.available ?: return
        scope.launch {
            _state.value = _state.value.copy(
                downloading = true, downloadProgress = null, installing = false,
                message = "Downloading ${info.versionName}…"
            )
            try {
                val apk = withContext(Dispatchers.IO) { download(info) }
                _state.value = _state.value.copy(
                    downloading = false, downloadProgress = null, installing = true,
                    message = "Starting installer…"
                )
                install(apk)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    downloading = false, downloadProgress = null, installing = false,
                    message = "Download failed: ${e.message}"
                )
            }
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub API returned ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            val tag = json.optString("tag_name").ifBlank { return null }
            val assets = json.optJSONArray("assets") ?: return null
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?: return null
            return UpdateInfo(tag.removePrefix("v"), apkUrl, json.optString("body"))
        }
    }

    /** Streams the APK to disk in chunks, reporting [UpdateUiState.downloadProgress] as it goes. */
    private fun download(info: UpdateInfo): File {
        val dest = File(context.getExternalFilesDir(null), "finance-tracker-update.apk")
        if (dest.exists()) dest.delete()
        val req = Request.Builder().url(info.downloadUrl).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (${resp.code})")
            val body = resp.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 }
            var bytesRead = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesRead += read
                        if (total != null) {
                            // Only push a state update when the whole percent changes, so a fast
                            // connection does not flood the StateFlow with a recomposition per chunk.
                            val percent = ((bytesRead * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = _state.value.copy(downloadProgress = percent / 100f)
                            }
                        }
                    }
                }
            }
        }
        return dest
    }

    private fun install(apk: File) {
        // Sideloaded installs need a one-time per-app "install unknown apps" grant. Without this
        // check the system just shows a generic refusal and the update quietly goes nowhere, so
        // send the user straight to this app's toggle instead.
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = _state.value.copy(
                installing = false,
                message = "Allow Finance Tracker to install apps, then tap Update again."
            )
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val REPO = "bellavaffa-cmd/finance-tracker-app"

        /** True if dotted version [latest] is greater than [current] (e.g. "1.12.0" > "1.11.0"). */
        fun isNewer(latest: String, current: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").split(".", "-").map { it.toIntOrNull() ?: 0 }
            val l = parts(latest)
            val c = parts(current)
            for (i in 0 until maxOf(l.size, c.size)) {
                val a = l.getOrElse(i) { 0 }
                val b = c.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
