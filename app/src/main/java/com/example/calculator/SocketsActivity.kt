package com.example.calculator

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ

class SocketsActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var btnSend: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sockets)

        tvLog = findViewById(R.id.tvSockets)
        etServerIp = findViewById(R.id.etServerIp)
        etServerPort = findViewById(R.id.etServerPort)
        btnSend = findViewById(R.id.btnSendToPC)

        // Загружаем сохранённые настройки (из SettingsManager)
        val settings = SettingsManager(this)
        etServerIp.setText(settings.getServerIp())
        etServerPort.setText(settings.getServerPort().toString())

        btnSend.setOnClickListener {
            sendToServer()
        }
    }

    private fun sendToServer() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().toIntOrNull()
        if (ip.isEmpty() || port == null) {
            Toast.makeText(this, "Введите IP и порт", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val context = ZContext()
                val socket = context.createSocket(SocketType.REQ)
                socket.receiveTimeOut = 5000
                val address = "tcp://$ip:$port"
                socket.connect(address)

                val msg = "Hello from Android! (test)"
                socket.send(msg.toByteArray(ZMQ.CHARSET), 0)
                Log.d("SocketsActivity", "Sent: $msg")

                val reply = socket.recv(0)
                val replyStr = String(reply, ZMQ.CHARSET)
                Log.d("SocketsActivity", "Received: $replyStr")

                handler.post {
                    tvLog.text = "Отправлено: $msg\nОтвет сервера: $replyStr"
                }
                socket.close()
                context.close()
            } catch (e: Exception) {
                Log.e("SocketsActivity", "Error: ${e.message}")
                handler.post {
                    tvLog.text = "Ошибка: ${e.message}"
                }
            }
        }.start()
    }
}