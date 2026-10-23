package com.namal.qmgstudio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks the newest GitHub Release of the repo, downloads its APK and hands it to the system installer. */
object Updater {
    private const val API = "https://api.github.com/repos/xBooM-SL/Qmg-studio/releases/latest"

    data class Info(
        val version: String,   // "1.1"
        val apkUrl: String,
        val size: Long,
        val notes: String
    )

    /** "v1.10" / "1.10" -> [1, 10] */
    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun installedVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "QmgStudio-Updater")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    /** Returns the new release if one is newer than the installed app, null if up to date. Throws on network errors. */
    fun check(ctx: Context): Info? {
        val c = open(API)
        try {
            if (c.responseCode != 200) throw RuntimeException("GitHub " + c.responseCode)
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.optString("tag_name", "")
            if (tag.isEmpty() || !isNewer(tag, installedVersion(ctx))) return null
            val assets = j.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return Info(
                        version = tag.removePrefix("v").removePrefix("V"),
                        apkUrl = a.getString("browser_download_url"),
                        size = a.optLong("size", 0L),
                        notes = j.optString("body", "").trim().take(500)
                    )
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    /** Downloads the APK into the app cache. progress goes 0f..1f. Throws on failure. */
    fun download(ctx: Context, info: Info, progress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "QmgStudio-v" + info.version + ".apk")
        val c = open(info.apkUrl)
        try {
            if (c.responseCode != 200) throw RuntimeException("Download failed (" + c.responseCode + ")")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            c.inputStream.use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (out.length() < 100_000L) throw RuntimeException("Downloaded file is too small")
            return out
        } finally {
            c.disconnect()
        }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Opens Android's "allow installs from this app" screen. */
    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Hands the APK to the system package installer (it shows the normal "Update" screen). */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
