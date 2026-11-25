package com.better.scyllanizer.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val apkUrl: String
)

object UpdateChecker {
    private const val GITHUB_API = "https://api.github.com/repos/AeveaScylla/ScyllaNizer/releases/latest"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersion = packageInfo.versionName ?: return@withContext null

            val url = URL(GITHUB_API)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.getString("tag_name").removePrefix("v")
                val body = json.optString("body", "No release notes provided.")

                if (tagName != currentVersion) {
                    val assets = json.getJSONArray("assets")
                    var apkUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    if (apkUrl.isNotEmpty()) return@withContext UpdateInfo(tagName, body, apkUrl)
                }
            }
        } catch (_: Exception) {
            // Offline devices and API failures are intentionally silent.
        }
        null
    }

    fun downloadApk(context: Context, url: String, version: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("ScyllaNizer v$version Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "ScyllaNizer_v$version.apk"
            )
            .setMimeType("application/vnd.android.package-archive")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
