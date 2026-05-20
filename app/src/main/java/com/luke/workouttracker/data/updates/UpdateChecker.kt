package com.luke.workouttracker.data.updates

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.luke.workouttracker.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(
        val latestVersion: String,
        val currentVersion: String,
        val apkUrl: String,
        val releaseUrl: String,
        val sizeBytes: Long,
    ) : UpdateResult
    data class Error(val message: String) : UpdateResult
}

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val name: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
)

@Singleton
class UpdateChecker @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.github.com/repos/" +
                    "${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val code = conn.responseCode
            when {
                code == 404 -> return@runCatching UpdateResult.Error("No GitHub releases published yet.")
                code !in 200..299 -> return@runCatching UpdateResult.Error("GitHub returned HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString(GhRelease.serializer(), body)
            val latest = release.tagName.removePrefix("v").removePrefix("V")
            val current = BuildConfig.VERSION_NAME
            if (compareVersions(latest, current) <= 0) {
                UpdateResult.UpToDate
            } else {
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@runCatching UpdateResult.Error(
                        "Release ${release.tagName} has no APK attached.",
                    )
                UpdateResult.Available(
                    latestVersion = latest,
                    currentVersion = current,
                    apkUrl = apk.browserDownloadUrl,
                    releaseUrl = release.htmlUrl,
                    sizeBytes = apk.size,
                )
            }
        }.getOrElse { UpdateResult.Error(it.message ?: it.javaClass.simpleName) }
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, "update.apk").also { if (it.exists()) it.delete() }
        var current = URL(url)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (current.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                val loc = conn.getHeaderField("Location") ?: error("Redirect without Location")
                current = URL(current, loc)
                conn.disconnect()
                hops++
                continue
            }
            if (code !in 200..299) error("Download HTTP $code")
            break
        }
        val total = conn.contentLengthLong.coerceAtLeast(1L)
        conn.inputStream.use { input ->
            outFile.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    read += n
                    onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        outFile
    }

    /**
     * Returns true if the system installer was launched, false if the user was redirected to
     * grant the "install unknown apps" permission first.
     */
    fun launchInstaller(activity: Activity, apk: File): Boolean {
        val pm = activity.packageManager
        if (!pm.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )
            activity.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk,
        )
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(install)
        return true
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
