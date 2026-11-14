package com.better.scyllanizer.data

import android.content.Context

data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val apkUrl: String
)

/**
 * F-Droid builds receive updates through the F-Droid client.
 * This implementation intentionally performs no network checks or APK downloads.
 */
object UpdateChecker {
    suspend fun checkForUpdate(context: Context): UpdateInfo? = null

    fun downloadApk(context: Context, url: String, version: String) {
        // Intentionally unavailable in the F-Droid build.
    }
}
