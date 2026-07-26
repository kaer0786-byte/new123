package com.script.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * 前台保活服务:持续通知 + WakeLock,确保锁屏/后台脚本不被杀。
 * 需配合"忽略电池优化"权限(见 MainActivity 的引导)。
 */
class KeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "auto_engine_channel"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "自动化引擎", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoScriptEngine 运行中")
            .setContentText("自动化脚本引擎保持后台运行")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoEngine::WakeLock").apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L) // 最多 1 小时,防泄漏
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
