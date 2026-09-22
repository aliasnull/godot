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
import java.net.ServerSocket
import java.net.Socket

class McpBridgeService : Service() {

	private var serverSocket: ServerSocket? = null
	private var godot: Godot? = null

    companion object {
        private const val CHANNEL_ID = "aliasnull_mcp_bridge"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

        godot = Godot.getInstance(applicationContext)

          android.util.Log.e(
          "ALIASNULL_MCP",
          "Godot instance acquired. status=${godot?.runStatus}"
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
        val output = socket.getOutputStream()

        val requestLine = reader.readLine() ?: return

        // Read HTTP headers.
        var contentLength = 0

        while (true) {
            val header = reader.readLine() ?: return

            if (header.isEmpty()) {
                break
            }

            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }

        val body = CharArray(contentLength)

        var totalRead = 0
        while (totalRead < contentLength) {
            val count = reader.read(body, totalRead, contentLength - totalRead)
            if (count == -1) {
                break
            }
            totalRead += count
        }

        val requestBody = String(body, 0, totalRead)

        android.util.Log.e(
            "ALIASNULL_MCP",
            "JSON-RPC request: $requestBody"
        )

		val requestId = Regex("\"id\"\\s*:\\s*([^,}\\s]+)")
             .find(requestBody)
             ?.groupValues
             ?.get(1)
             ?: "null"

        val responseBody = when {
            requestBody.contains("\"method\":\"initialize\"") ||
            requestBody.contains("\"method\": \"initialize\"") -> {
                """
                {
                  "jsonrpc": "2.0",
                  "id": $requestId,
                  "result": {
                    "protocolVersion": "2025-06-18",
                    "capabilities": {
                      "tools": {}
                    },
                    "serverInfo": {
                      "name": "ALIASNULL Godot MCP",
                      "version": "0.1.0"
                    }
                  }
                }
                """.trimIndent()
            }

			requestBody.contains("\"method\":\"tools/list\"") ||
            requestBody.contains("\"method\": \"tools/list\"") -> {
               """
               {
                  "jsonrpc": "2.0",
                  "id": $requestId,
                  "result": {
                    "tools": [
                      {
                        "name": "ping_godot",
                        "description": "Check whether the native ALIASNULL Godot bridge is alive.",
                        "inputSchema": {
                        "type": "object",
                        "properties": {}
                       }
                     }
                   ]
                 }
               }
              """.trimIndent()
           }


		       requestBody.contains("\"method\":\"tools/call\"") ||
               requestBody.contains("\"method\": \"tools/call\"") -> {
               if (requestBody.contains("\"name\":\"ping_godot\"") ||
               requestBody.contains("\"name\": \"ping_godot\"")) {
                  """
                  {
                    "jsonrpc": "2.0",
                    "id": $requestId,
                    "result": {
                    "content": [
                    {
                      "type": "text",
                      "text": "ALIASNULL Godot native MCP bridge is alive. runStatus=${godot?.runStatus}, initialized=${godot?.isInitialized()}"
                   }
                 ]
               }
             }
                """.trimIndent()
        } else {
        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "error": {
            "code": -32602,
            "message": "Unknown tool"
          }
        }
        """.trimIndent()
    }
}
	else -> {
                """
                {
                  "jsonrpc": "2.0",
                  "id": $requestId,
                  "error": {
                    "code": -32601,
                    "message": "Method not implemented"
                  }
                }
                """.trimIndent()
            }
        }

        val responseBytes = responseBody.toByteArray(Charsets.UTF_8)

        val responseHeaders =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${responseBytes.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"

        output.write(responseHeaders.toByteArray(Charsets.UTF_8))
        output.write(responseBytes)
        output.flush()
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
