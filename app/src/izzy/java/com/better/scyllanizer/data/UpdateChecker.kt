package com.better.scyllanizer.data

import android.content.Context

/**
 * IzzyOnDroid flavor intentionally has no in-app APK updater.
 * Updates are distributed by the IzzyOnDroid repository.
 */
data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val apkUrl: String
)

object UpdateChecker {
    @Suppress("UNUSED_PARAMETER")
    suspend fun checkForUpdate(context: Context): UpdateInfo? = null
}
