package com.velogappie.app.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.velogappie.app.MainActivity
import com.velogappie.app.R

class ConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("bike_connection")
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_connection_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { setSound(null, null) }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                onDisconnectRequested?.invoke()
                return START_NOT_STICKY
            }
            ACTION_STOP_SCAN -> {
                onStopScanRequested?.invoke()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CONNECTED
                updateNotification(
                    intent.getStringExtra(EXTRA_TITLE) ?: "",
                    intent.getStringExtra(EXTRA_TEXT) ?: "",
                    mode,
                )
                return START_STICKY
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_CONNECTED

        startForeground(NOTIF_ID, buildNotification(title, text, mode))
        return START_STICKY
    }

    private fun buildNotification(title: String, text: String, mode: String): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_bike)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )

        when (mode) {
            MODE_SCANNING -> builder.addAction(
                0,
                getString(R.string.scan_stop),
                PendingIntent.getService(
                    this, 2,
                    Intent(this, ConnectionService::class.java).setAction(ACTION_STOP_SCAN).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            else -> builder.addAction(
                0,
                getString(R.string.info_disconnect),
                PendingIntent.getService(
                    this, 1,
                    Intent(this, ConnectionService::class.java).setAction(ACTION_DISCONNECT).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        return builder.build()
    }

    private fun updateNotification(title: String, text: String, mode: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(title, text, mode))
    }

    override fun onDestroy() {
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "bike_status"
        private const val NOTIF_ID = 9010
        private const val ACTION_DISCONNECT = "disconnect_bike"
        private const val ACTION_STOP_SCAN = "stop_scan"
        private const val ACTION_UPDATE = "update_notification"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_MODE = "mode"
        const val MODE_SCANNING = "scanning"
        const val MODE_CONNECTED = "connected"

        var onDisconnectRequested: (() -> Unit)? = null
        var onStopScanRequested: (() -> Unit)? = null

        fun start(context: Context, title: String, text: String, mode: String = MODE_CONNECTED) {
            context.startForegroundService(
                Intent(context, ConnectionService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_MODE, mode)
            )
        }

        fun update(context: Context, title: String, text: String, mode: String = MODE_CONNECTED) {
            context.startService(
                Intent(context, ConnectionService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_MODE, mode)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }
}
