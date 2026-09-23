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
import org.godotengine.godot.GodotLib
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.io.File

class McpBridgeService : Service() {

	private var serverSocket: ServerSocket? = null
	private var godot: Godot? = null
	private val RENDER_THREAD_TIMEOUT_MS = 2000L

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

		val globalKey = Regex("\"key\"\\s*:\\s*\"([^\"]*)\"")
    .find(requestBody)
    ?.groupValues
    ?.get(1)

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
          },
          {
            "name": "get_global",
            "description": "Read a Godot global/project property.",
            "inputSchema": {
              "type": "object",
              "properties": {
                "key": {
                  "type": "string",
                  "description": "Godot global property key."
                }
              },
              "required": ["key"]
            }
          },
          {
             "name": "get_project_resource_dir",
             "description": "Get the current Godot project resource directory.",
             "inputSchema": {
                "type": "object",
                "properties": {}
                }
               },
			   {
                "name": "get_project_settings",
                "description": "Read basic settings from the currently open Godot project.",
                "inputSchema": {
                "type": "object",
                "properties": {}
                }
               },
			   {
                 "name": "get_editor_state",
                 "description": "Get the current native Godot editor/runtime state.",
                 "inputSchema": {
                 "type": "object",
                "properties": {}
               }
              },
	        {
             "name": "get_open_scenes",
             "description": "Get all currently open scenes in the Godot editor.",
             "inputSchema": {
             "type": "object",
             "properties": {}
            }
           },
         {
          "name": "get_unsaved_scenes",
          "description": "Get all currently unsaved scenes in the Godot editor.",
          "inputSchema": {
          "type": "object",
          "properties": {}
         }
        },
      {
       "name": "get_current_scene",
       "description": "Get the path of the scene currently being edited.",
       "inputSchema": {
       "type": "object",
       "properties": {}
      }
    },
      {
        "name": "get_play_state",
        "description": "Get the current Godot editor play state and playing scene.",
        "inputSchema": {
        "type": "object",
        "properties": {}
        }
       },
               {
                 "name": "read_project_file",
                 "description": "Read a text file from the currently open Godot project.",
                 "inputSchema": {
                 "type": "object",
                 "properties": {
                 "path": {
                    "type": "string",
                    "description": "Project-relative file path, for example project.godot."
                  }
                 },
                 "required": ["path"]
                }
              },
			  {
               "name": "list_project_files",
               "description": "List files and directories inside the current Godot project.",
               "inputSchema": {
               "type": "object",
               "properties": {
               "path": {
                 "type": "string",
                 "description": "Project-relative directory path. Leave empty to list the project root."
                }
               }
              }
             },
		    {
            "name": "write_project_file",
            "description": "Create or overwrite a text file inside the current Godot project.",
            "inputSchema": {
            "type": "object",
            "properties": {
               "path": {
               "type": "string",
               "description": "Project-relative file path to create or overwrite."
               },
                "content": {
                "type": "string",
                "description": "Text content to write into the file."
               }
             },
             "required": ["path", "content"]
            }
           },
		   {
            "name": "delete_project_file",
            "description": "Delete a file inside the current Godot project.",
            "inputSchema": {
               "type": "object",
                 "properties": {
                    "path": {
                       "type": "string",
                       "description": "Project-relative file path to delete."
                      }
                     },
                      "required": ["path"]
                 }
               },
			   {
                "name": "create_project_directory",
                "description": "Create a directory inside the current Godot project.",
                "inputSchema": {
                "type": "object",
                "properties": {
                   "path": {
                     "type": "string",
                     "description": "Project-relative directory path to create."
                    }
                  },
                   "required": ["path"]
                 }
               },
			   {
                "name": "delete_project_directory",
                "description": "Delete an empty directory inside the current Godot project.",
                "inputSchema": {
                   "type": "object",
                   "properties": {
                      "path": {
                        "type": "string",
                        "description": "Project-relative directory path to delete."
                       }
                     },
                     "required": ["path"]
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

    } else if (requestBody.contains("\"name\":\"get_global\"") ||
               requestBody.contains("\"name\": \"get_global\"")) {

        val key = globalKey

        if (key == null) {
            """
            {
              "jsonrpc": "2.0",
              "id": $requestId,
              "error": {
                "code": -32602,
                "message": "Missing key"
              }
            }
            """.trimIndent()
        } else {
            val value = getGodotGlobal(key)

            """
            {
              "jsonrpc": "2.0",
              "id": $requestId,
              "result": {
                "content": [
                  {
                    "type": "text",
                    "text": "$value"
                  }
                ]
              }
            }
            """.trimIndent()
        }

    } else if (requestBody.contains("\"name\":\"get_project_resource_dir\"") ||
               requestBody.contains("\"name\": \"get_project_resource_dir\"")) {

        val value = getGodotProjectResourceDir()

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "$value"
              }
            ]
          }
        }
        """.trimIndent()

		    } else if (requestBody.contains("\"name\":\"read_project_file\"") ||
               requestBody.contains("\"name\": \"read_project_file\"")) {

        val path = Regex("\"path\"\\s*:\\s*\"([^\"]*)\"")
            .find(requestBody)
            ?.groupValues
            ?.get(1)

        if (path == null) {
            """
            {
              "jsonrpc": "2.0",
              "id": $requestId,
              "error": {
                "code": -32602,
                "message": "Missing path"
              }
            }
            """.trimIndent()
        } else {
            val value = readProjectFile(path)

            """
            {
              "jsonrpc": "2.0",
              "id": $requestId,
              "result": {
                "content": [
                  {
                    "type": "text",
                    "text": "${jsonEscape(value)}"
                  }
                ]
              }
            }
            """.trimIndent()
        } 

		} else if (requestBody.contains("\"name\":\"list_project_files\"") ||
           requestBody.contains("\"name\": \"list_project_files\"")) {

    val path = Regex("\"path\"\\s*:\\s*\"([^\"]*)\"")
        .find(requestBody)
        ?.groupValues
        ?.get(1)
        ?: ""

    val value = listProjectFiles(path)

    """
    {
      "jsonrpc": "2.0",
      "id": $requestId,
      "result": {
        "content": [
          {
            "type": "text",
            "text": "${jsonEscape(value)}"
          }
        ]
      }
    }
    """.trimIndent()

	} else if (requestBody.contains("\"name\":\"write_project_file\"") ||
           requestBody.contains("\"name\": \"write_project_file\"")) {

    val path = Regex("\"path\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        .find(requestBody)
        ?.groupValues
        ?.get(1)

    val content = Regex("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        .find(requestBody)
        ?.groupValues
        ?.get(1)

    if (path == null || content == null) {
        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "error": {
            "code": -32602,
            "message": "Missing path or content"
          }
        }
        """.trimIndent()
    } else {
        val value = writeProjectFile(
         jsonUnescape(path),
         jsonUnescape(content)
       )

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
              }
            ]
          }
        }
        """.trimIndent()
    }

	} else if (requestBody.contains("\"name\":\"delete_project_file\"") ||
           requestBody.contains("\"name\": \"delete_project_file\"")) {

    val path = Regex("\"path\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        .find(requestBody)
        ?.groupValues
        ?.get(1)

    if (path == null) {
        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "error": {
            "code": -32602,
            "message": "Missing path"
          }
        }
        """.trimIndent()
    } else {
        val value = deleteProjectFile(
            jsonUnescape(path)
        )

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
              }
            ]
          }
        }
        """.trimIndent()
    }

	} else if (requestBody.contains("\"name\":\"create_project_directory\"") ||
           requestBody.contains("\"name\": \"create_project_directory\"")) {

    val path = Regex("\"path\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        .find(requestBody)
        ?.groupValues
        ?.get(1)

    if (path == null) {
        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "error": {
            "code": -32602,
            "message": "Missing path"
          }
        }
        """.trimIndent()
    } else {
        val value = createProjectDirectory(
            jsonUnescape(path)
        )

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
              }
            ]
          }
        }
        """.trimIndent()
    }

	} else if (requestBody.contains("\"name\":\"delete_project_directory\"") ||
           requestBody.contains("\"name\": \"delete_project_directory\"")) {

    val path = Regex("\"path\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        .find(requestBody)
        ?.groupValues
        ?.get(1)

    if (path == null) {
        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "error": {
            "code": -32602,
            "message": "Missing path"
          }
        }
        """.trimIndent()
    } else {
        val value = deleteProjectDirectory(
            jsonUnescape(path)
        )

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
              }
            ]
          }
        }
        """.trimIndent()
    }

	} else if (requestBody.contains("\"name\":\"get_project_settings\"") ||
           requestBody.contains("\"name\": \"get_project_settings\"")) {

    val value = getGodotProjectSettings()

    """
    {
      "jsonrpc": "2.0",
      "id": $requestId,
      "result": {
        "content": [
          {
            "type": "text",
            "text": "${jsonEscape(value)}"
          }
        ]
      }
    }
    """.trimIndent()

	} else if (requestBody.contains("\"name\":\"get_editor_state\"") ||
           requestBody.contains("\"name\": \"get_editor_state\"")) {

    val value = getEditorState()

    """
    {
      "jsonrpc": "2.0",
      "id": $requestId,
      "result": {
        "content": [
          {
            "type": "text",
            "text": "${jsonEscape(value)}"
          }
        ]
      }
    }
    """.trimIndent()

	    } else if (requestBody.contains("\"name\":\"get_open_scenes\"") ||
               requestBody.contains("\"name\": \"get_open_scenes\"")) {

        val value = getGodotOpenScenes()

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
              }
            ]
          }
        }
        """.trimIndent()

    } else if (requestBody.contains("\"name\":\"get_unsaved_scenes\"") ||
               requestBody.contains("\"name\": \"get_unsaved_scenes\"")) {

        val value = getGodotUnsavedScenes()

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
              }
            ]
          }
        }
        """.trimIndent()

    } else if (requestBody.contains("\"name\":\"get_current_scene\"") ||
               requestBody.contains("\"name\": \"get_current_scene\"")) {

        val value = getGodotCurrentScene()

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
              }
            ]
          }
        }
        """.trimIndent()

    } else if (requestBody.contains("\"name\":\"get_play_state\"") ||
               requestBody.contains("\"name\": \"get_play_state\"")) {

        val value = getGodotPlayState()

        """
        {
          "jsonrpc": "2.0",
          "id": $requestId,
          "result": {
            "content": [
              {
                "type": "text",
                "text": "${jsonEscape(value)}"
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

	private fun runOnRenderThreadWithTimeout(
    action: () -> String
): String {
    val result = AtomicReference<String>("")
    val latch = CountDownLatch(1)

    godot?.runOnRenderThread(
        Runnable {
            try {
                result.set(action())
            } catch (e: Exception) {
                result.set("ERROR: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
    )

    if (!latch.await(RENDER_THREAD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
        return "ERROR: Godot render thread did not respond within ${RENDER_THREAD_TIMEOUT_MS}ms."
    }

    return result.get()
}

	private fun getGodotGlobal(key: String): String {
    return runOnRenderThreadWithTimeout {
        GodotLib.getGlobal(key).toString()
    }
}


	private fun getGodotProjectResourceDir(): String {
    return runOnRenderThreadWithTimeout {
        GodotLib.getProjectResourceDir()
    }
}

	private fun getGodotProjectSettings(): String {
    return runOnRenderThreadWithTimeout {
        val name = GodotLib.getGlobal("application/config/name").toString()
        val features = GodotLib.getGlobal("application/config/features").toString()

        "name=$name\nfeatures=$features"
    }
}

	private fun isGodotProjectOpen(): Boolean {
    val projectDir = File(getGodotProjectResourceDir()).canonicalFile

    return projectDir.path != File.separator &&
           projectDir.isDirectory &&
           File(projectDir, "project.godot").isFile
}

	private fun getGodotOpenScenes(): String {
    val result = AtomicReference<String>("")
    val latch = CountDownLatch(1)

    godot?.runOnHostThread(
        Runnable {
            try {
                result.set(GodotLib.getOpenScenes())
            } catch (e: Exception) {
                result.set("ERROR: Failed to get open scenes: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
    )

    if (!latch.await(RENDER_THREAD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
        return "ERROR: Godot host thread did not respond within ${RENDER_THREAD_TIMEOUT_MS}ms."
    }

    return result.get()
}

	private fun getGodotUnsavedScenes(): String {
    return try {
        GodotLib.getUnsavedScenes()
    } catch (e: Exception) {
        "ERROR: Failed to get unsaved scenes: ${e.message}"
    }
}

private fun getGodotCurrentScene(): String {
    return try {
        GodotLib.getCurrentScene()
    } catch (e: Exception) {
        "ERROR: Failed to get current scene: ${e.message}"
    }
}

private fun getGodotPlayState(): String {
    return try {
        GodotLib.getPlayState()
    } catch (e: Exception) {
        "ERROR: Failed to get play state: ${e.message}"
    }
}

	private fun getEditorState(): String {
    val initialized = godot?.isInitialized() == true
    val projectOpen = isGodotProjectOpen()
    val status = godot?.runStatus?.toString() ?: "UNKNOWN"

    return "initialized=$initialized\nproject_open=$projectOpen\nrun_status=$status"
}

	private fun readProjectFile(relativePath: String): String {
    val projectDir = File(getGodotProjectResourceDir()).canonicalFile

    if (projectDir.path == File.separator || !projectDir.isDirectory) {
        return "ERROR: No Godot project is currently open."
    }

    val file = File(projectDir, relativePath).canonicalFile

    if (file != projectDir &&
        !file.path.startsWith(projectDir.path + File.separator)) {
        return "ERROR: Path is outside the current Godot project."
    }

    if (!file.exists()) {
        return "ERROR: File not found: $relativePath"
    }

    if (!file.isFile) {
    return "ERROR: Not a file: $relativePath"
}

val maxFileSize = 1024L * 1024L

if (file.length() > maxFileSize) {
    return "ERROR: File is too large. Maximum supported size is 1 MB."
}

return file.readText()
}

	private fun jsonEscape(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

	private fun jsonUnescape(value: String): String {
    val result = StringBuilder()
    var i = 0

    while (i < value.length) {
        val current = value[i]

        if (current == '\\' && i + 1 < value.length) {
            when (value[i + 1]) {
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                '"' -> result.append('"')
                '\\' -> result.append('\\')
                else -> {
                    result.append('\\')
                    result.append(value[i + 1])
                }
            }

            i += 2
        } else {
            result.append(current)
            i++
        }
    }

    return result.toString()
}
	
	private fun listProjectFiles(relativePath: String = ""): String {
    val projectDir = File(getGodotProjectResourceDir()).canonicalFile

    if (projectDir.path == File.separator || !projectDir.isDirectory) {
        return "ERROR: No Godot project is currently open."
    }

    val targetDir = File(projectDir, relativePath).canonicalFile

    if (targetDir != projectDir &&
        !targetDir.path.startsWith(projectDir.path + File.separator)) {
        return "ERROR: Path is outside the current Godot project."
    }

    if (!targetDir.exists()) {
        return "ERROR: Directory not found: $relativePath"
    }

    if (!targetDir.isDirectory) {
        return "ERROR: Not a directory: $relativePath"
    }

    val entries = targetDir.listFiles()
    ?.sortedBy { it.name.lowercase() }
    ?.take(500)
    ?: emptyList()

    return buildString {
        for (entry in entries) {
    if (entry.isDirectory &&
        (entry.name == ".godot" || entry.name == ".git")) {
        continue
    }

    if (entry.isDirectory) {
        append("[DIR] ")
    } else {
        append("[FILE] ")
    }

    append(entry.name)
    append('\n')
}
        
    }.trimEnd()
}

   private fun writeProjectFile(relativePath: String, content: String): String {
    val projectDir = File(getGodotProjectResourceDir()).canonicalFile

    if (projectDir.path == File.separator || !projectDir.isDirectory) {
        return "ERROR: No Godot project is currently open."
    }

    val file = File(projectDir, relativePath).canonicalFile

    if (file == projectDir ||
        !file.path.startsWith(projectDir.path + File.separator)) {
        return "ERROR: Path is outside the current Godot project."
    }

    if (file.exists() && !file.isFile) {
        return "ERROR: Target path is not a file."
    }

    val maxFileSize = 1024L * 1024L

    if (content.toByteArray(Charsets.UTF_8).size > maxFileSize) {
        return "ERROR: File content is too large. Maximum supported size is 1 MB."
    }

    try {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return "OK: File written successfully: $relativePath"
    } catch (e: Exception) {
        return "ERROR: Failed to write file: ${e.message}"
    }
}

   private fun deleteProjectFile(relativePath: String): String {
    val projectDir = File(getGodotProjectResourceDir()).canonicalFile

    if (projectDir.path == File.separator || !projectDir.isDirectory) {
        return "ERROR: No Godot project is currently open."
    }

    val file = File(projectDir, relativePath).canonicalFile

    if (file == projectDir ||
        !file.path.startsWith(projectDir.path + File.separator)) {
        return "ERROR: Path is outside the current Godot project."
    }

    if (!file.exists()) {
        return "ERROR: File not found: $relativePath"
    }

    if (!file.isFile) {
        return "ERROR: Target is not a file."
    }

    return try {
        if (file.delete()) {
            "OK: File deleted successfully: $relativePath"
        } else {
            "ERROR: Failed to delete file: $relativePath"
        }
    } catch (e: Exception) {
        "ERROR: Failed to delete file: ${e.message}"
    }
}

   private fun deleteProjectDirectory(relativePath: String): String {
    val projectDir = File(getGodotProjectResourceDir()).canonicalFile

    if (projectDir.path == File.separator || !projectDir.isDirectory) {
        return "ERROR: No Godot project is currently open."
    }

    val directory = File(projectDir, relativePath).canonicalFile

    if (directory == projectDir ||
        !directory.path.startsWith(projectDir.path + File.separator)) {
        return "ERROR: Path is outside the current Godot project."
    }

    if (!directory.exists()) {
        return "ERROR: Directory not found: $relativePath"
    }

    if (!directory.isDirectory) {
        return "ERROR: Target is not a directory."
    }

    val entries = directory.listFiles()

    if (entries != null && entries.isNotEmpty()) {
        return "ERROR: Directory is not empty: $relativePath"
    }

    return try {
        if (directory.delete()) {
            "OK: Directory deleted successfully: $relativePath"
        } else {
            "ERROR: Failed to delete directory: $relativePath"
        }
    } catch (e: Exception) {
        "ERROR: Failed to delete directory: ${e.message}"
    }
}

   private fun createProjectDirectory(relativePath: String): String {
    val projectDir = File(getGodotProjectResourceDir()).canonicalFile

    if (projectDir.path == File.separator || !projectDir.isDirectory) {
        return "ERROR: No Godot project is currently open."
    }

    val directory = File(projectDir, relativePath).canonicalFile

    if (directory == projectDir ||
        !directory.path.startsWith(projectDir.path + File.separator)) {
        return "ERROR: Path is outside the current Godot project."
    }

    if (directory.exists()) {
        return if (directory.isDirectory) {
            "OK: Directory already exists: $relativePath"
        } else {
            "ERROR: Target path is an existing file."
        }
    }

    return try {
        if (directory.mkdirs()) {
            "OK: Directory created successfully: $relativePath"
        } else {
            "ERROR: Failed to create directory: $relativePath"
        }
    } catch (e: Exception) {
        "ERROR: Failed to create directory: ${e.message}"
    }
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
