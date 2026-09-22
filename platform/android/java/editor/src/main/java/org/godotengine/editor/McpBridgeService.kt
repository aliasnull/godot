package org.godotengine.editor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.godotengine.godot.Godot
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class McpBridgeService : Service() {

	private var serverSocket: ServerSocket? = null

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

		startMcpServer()
    }

	private fun handleClient(client: Socket) {
    client.use { socket ->
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)

        val request = reader.readLine()

        android.util.Log.e(
            "ALIASNULL_MCP",
            "Received: $request"
        )

        writer.println(
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: 2\r\n" +
            "Connection: close\r\n" +
            "\r\n" +
            "{}"
        )
    }
}

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

	private fun startMcpServer() {
    Thread {
        try {
            serverSocket = ServerSocket(8765, 50, java.net.InetAddress.getByName("127.0.0.1"))

            while (!Thread.currentThread().isInterrupted) {
                val client = serverSocket?.accept()
                if (client != null) {
                    handleClient(client)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ALIASNULL_MCP", "MCP server stopped", e)
        }
    }.start()
}

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
