package org.godotengine.editor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.godotengine.godot.Godot

class McpBridgeService : Service() {

    companion object {
        private const val CHANNEL_ID = "aliasnull_mcp_bridge"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

        val godot = Godot.getInstance(applicationContext)

          android.util.Log.e(
          "ALIASNULL_MCP",
          "Godot instance acquired. status=${godot.runStatus}"
        )
		
        android.util.Log.e("ALIASNULL_MCP", "McpBridgeService.onCreate() CALLED")

        val notificationManager =
            getSystemService(NotificationManager::class.java)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ALIASNULL MCP Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ALIASNULL MCP Bridge")
            .setContentText("Native bridge is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

		android.util.Log.d("McpBridgeService", "Starting foreground MCP bridge")
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
