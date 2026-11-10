package com.better.scyllanizer.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.better.scyllanizer.MainActivity
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the local Wi-Fi Drop HTTP server alive while the app is in the background. */
class WifiDropService : Service() {
    private var server: LocalServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                val rootPath = intent.getStringExtra(EXTRA_ROOT_PATH)
                val initialDestination = intent.getStringExtra(EXTRA_INITIAL_DESTINATION).orEmpty()
                if (rootPath.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startForegroundCompat()
                try {
                    server?.stop()
                    server = LocalServer(PORT, File(rootPath)).also { it.start() }
                    _isRunning.value = true
                } catch (_: Exception) {
                    _isRunning.value = false
                    server?.stop()
                    server = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopServer() {
        server?.stop()
        server = null
        _isRunning.value = false
    }

    private fun startForegroundCompat() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("ScyllaNizer Wi-Fi Drop")
            .setContentText("Ready to receive files on your local network")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wi-Fi Drop",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ScyllaNizer ready to receive files over Wi-Fi"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.better.scyllanizer.action.WIFI_DROP_START"
        const val ACTION_STOP = "com.better.scyllanizer.action.WIFI_DROP_STOP"
        const val EXTRA_ROOT_PATH = "wifi_drop_root_path"
        const val EXTRA_INITIAL_DESTINATION = "wifi_drop_initial_destination"
        private const val PORT = 8080
        private const val CHANNEL_ID = "wifi_drop_service"
        private const val NOTIFICATION_ID = 8080

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
