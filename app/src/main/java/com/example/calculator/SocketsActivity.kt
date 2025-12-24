package com.example.calculator

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ

class SocketsActivity : AppCompatActivity() {

    private val logTag = "ZMQ_TAG"

    private lateinit var tvSockets: TextView
    private lateinit var btnStartInApp: Button
    private lateinit var btnSendToPC: Button
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sockets)

        tvSockets = findViewById(R.id.tvSockets)
        btnStartInApp = findViewById(R.id.btnStartInApp)
        btnSendToPC = findViewById(R.id.btnSendToPC)
        handler = Handler(Looper.getMainLooper())

        // Демонстрация обмена внутри приложения (эмуляция ZMQ REQ/REP)
        btnStartInApp.setOnClickListener {
            tvSockets.text = "Starting in-app ZMQ demo..."
            startInAppCommunication()
        }

        // Реальный клиент ZMQ → Python‑сервер на ПК
        btnSendToPC.setOnClickListener {
            startClientToPc()
        }
    }

    // ---------------- 1. Android ↔ Android (демо без реального сокета) ----------------

    private fun startInAppCommunication() {
        // «Сервер» и «клиент» как две последовательные задачи в отдельных потоках
        Thread {
            startServerInAppFake()
        }.start()

        Thread {
            Thread.sleep(500)
            startClientInAppFake()
        }.start()
    }

    private fun startServerInAppFake() {
        val request = "Hello from Android in-app client!"
        for (counter in 1..5) {
            Log.d(logTag, "[SERVER_FAKE] Received: $request")

            handler.post {
                tvSockets.text = "In-app server received: $request ($counter)"
            }

            Thread.sleep(400)

            val response = "Hello from Android ZMQ Server!"
            Log.d(logTag, "[SERVER_FAKE] Sent reply: $response")
        }
    }

    private fun startClientInAppFake() {
        val response = "Hello from Android ZMQ Server!"
        for (i in 1..5) {
            Log.d(logTag, "[CLIENT_FAKE] Sent: Hello from Android in-app client!")

            handler.post {
                tvSockets.text = "In-app client got: $response (message $i)"
            }

            Thread.sleep(400)
        }
    }

    private fun startClientToPc() {
        Thread {
            val context = ZContext()
            val socket = context.createSocket(SocketType.REQ)

            val serverIp = "192.168.0.105"
            val serverPort = 6000
            socket.connect("tcp://$serverIp:$serverPort")

            val msg = "Hello from Android!"
            socket.send(msg.toByteArray(ZMQ.CHARSET), 0)
            Log.d(logTag, "[CLIENT→PC] Sent: $msg")

            try {
                val reply = socket.recv(0)
                val replyStr = String(reply, ZMQ.CHARSET)
                Log.d(logTag, "[CLIENT→PC] Received: $replyStr")

                handler.post {
                    tvSockets.text = "Reply from PC: $replyStr"
                }
            } catch (e: Exception) {
                Log.e(logTag, "[CLIENT→PC] Error: ${e.message}")
                handler.post {
                    tvSockets.text = "Error talking to PC server: ${e.message}"
                }
            } finally {
                socket.close()
                context.close()
            }
        }.start()
    }
}
