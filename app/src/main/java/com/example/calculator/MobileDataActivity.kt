package com.example.calculator

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.text.SimpleDateFormat
import java.util.*

class MobileDataActivity : AppCompatActivity() {

    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvLastData: TextView
    private lateinit var tvPacketsSent: TextView
    private lateinit var tvLastSendTime: TextView
    private lateinit var btnStartSending: Button
    private lateinit var btnStopSending: Button
    private lateinit var btnTestConnection: Button

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsManager: SettingsManager
    private lateinit var trafficCollector: TrafficCollector

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val handler = Handler(Looper.getMainLooper())
    private var sending = false
    private var packetsSent = 0
    private var lastSendTime: Long = 0
    private var locationCallback: LocationCallback? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_data)

        etServerIp = findViewById(R.id.etServerIp)
        etServerPort = findViewById(R.id.etServerPort)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvLastData = findViewById(R.id.tvLastData)
        tvPacketsSent = findViewById(R.id.tvPacketsSent)
        tvLastSendTime = findViewById(R.id.tvLastSendTime)
        btnStartSending = findViewById(R.id.btnStartSending)
        btnStopSending = findViewById(R.id.btnStopSending)
        btnTestConnection = findViewById(R.id.btnTestConnection)

        tvLastData.movementMethod = ScrollingMovementMethod()

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsManager = SettingsManager(this)
        trafficCollector = TrafficCollector(this)

        loadSettings()

        btnTestConnection.setOnClickListener { testConnection() }
        btnStartSending.setOnClickListener { startSending() }
        btnStopSending.setOnClickListener { stopSending() }
    }

    private fun loadSettings() {
        etServerIp.setText(settingsManager.getServerIp())
        etServerPort.setText(settingsManager.getServerPort().toString())
    }

    private fun saveSettings() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().toIntOrNull() ?: SettingsManager.DEFAULT_PORT
        settingsManager.setServerIp(ip)
        settingsManager.setServerPort(port)
    }

    private fun testConnection() {
        saveSettings()
        updateConnectionStatus("Тестирование...", 0xFFA500)
        Thread {
            try {
                val context = ZContext()
                val socket = context.createSocket(SocketType.REQ)
                socket.receiveTimeOut = 3000
                val address = "tcp://${settingsManager.getServerIp()}:${settingsManager.getServerPort()}"
                socket.connect(address)
                val testMsg = """{"type":"test","message":"ping","timestamp":${System.currentTimeMillis()}}"""
                socket.send(testMsg.toByteArray(ZMQ.CHARSET), 0)
                val reply = socket.recv(0)
                if (reply != null) {
                    handler.post {
                        updateConnectionStatus("Соединение установлено ✓", 0x00FF00)
                        Toast.makeText(this, "Сервер доступен", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    handler.post {
                        updateConnectionStatus("Нет ответа от сервера", 0xFF0000)
                        Toast.makeText(this, "Сервер не ответил", Toast.LENGTH_SHORT).show()
                    }
                }
                socket.close()
                context.close()
            } catch (e: Exception) {
                handler.post {
                    updateConnectionStatus("Ошибка: ${e.message}", 0xFF0000)
                    Toast.makeText(this, "Ошибка соединения: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun updateConnectionStatus(status: String, color: Int) {
        tvConnectionStatus.text = status
        tvConnectionStatus.setTextColor(color)
    }

    @SuppressLint("MissingPermission")
    private fun startSending() {
        if (sending) return
        if (!hasLocationPermission() || !hasPhoneStatePermission()) {
            Toast.makeText(this, "Нет разрешений. Запустите приложение заново.", Toast.LENGTH_LONG).show()
            return
        }
        saveSettings()
        sending = true
        packetsSent = 0
        updateConnectionStatus("Отправка...", 0x0000FF)
        btnStartSending.isEnabled = false
        btnStopSending.isEnabled = true
        etServerIp.isEnabled = false
        etServerPort.isEnabled = false

        val locationRequest = LocationRequest.Builder(1000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!sending) return
                val loc = result.lastLocation ?: return
                val measurement = buildMeasurement(loc)
                handler.post {
                    tvLastData.text = gson.toJson(measurement)
                }
                sendToServer(measurement)
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        Toast.makeText(this, "Отправка начата", Toast.LENGTH_SHORT).show()
    }

    private fun stopSending() {
        sending = false
        updateConnectionStatus("Остановлено", 0xFF0000)
        btnStartSending.isEnabled = true
        btnStopSending.isEnabled = false
        etServerIp.isEnabled = true
        etServerPort.isEnabled = true
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun buildMeasurement(location: Location): MeasurementDto {
        val locDto = LocationDto(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            timestamp = System.currentTimeMillis(),
            speed = location.speed,
            accuracy = location.accuracy
        )

        val lteCells = mutableListOf<CellInfoLteDto>()
        val gsmCells = mutableListOf<CellInfoGsmDto>()
        val nrCells = mutableListOf<CellInfoNrDto>()

        try {
            val allCells = telephonyManager.allCellInfo
            allCells?.forEach { info ->
                when (info) {
                    is CellInfoLte -> {
                        val id = info.cellIdentity
                        val ss = info.cellSignalStrength
                        // Правильное получение Band (номер диапазона)
                        val band = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            id.bands.firstOrNull()
                        } else {
                            // Старые версии – используем bandwidth как приближение
                            id.bandwidth
                        }
                        lteCells.add(
                            CellInfoLteDto(
                                band = band,
                                earfcn = id.earfcn,
                                mcc = id.mccString?.toIntOrNull(),
                                mnc = id.mncString?.toIntOrNull(),
                                pci = id.pci,
                                tac = id.tac,
                                rsrp = ss.rsrp,
                                rsrq = ss.rsrq,
                                rssnr = ss.rssnr,
                                cqi = ss.cqi,
                                timingAdvance = ss.timingAdvance
                            )
                        )
                    }
                    is CellInfoGsm -> {
                        val id = info.cellIdentity
                        val ss = info.cellSignalStrength
                        gsmCells.add(
                            CellInfoGsmDto(
                                cid = id.cid,
                                lac = id.lac,
                                mcc = id.mccString?.toIntOrNull(),
                                mnc = id.mncString?.toIntOrNull(),
                                bsic = id.bsic,
                                arfcn = id.arfcn,
                                rssi = ss.dbm,
                                timingAdvance = ss.timingAdvance
                            )
                        )
                    }
                    is CellInfoNr -> {
                        val id = info.cellIdentity as CellIdentityNr
                        val ss = info.cellSignalStrength as CellSignalStrengthNr
                        nrCells.add(
                            CellInfoNrDto(
                                nci = id.nci,
                                nrarfcn = id.nrarfcn,
                                tac = id.tac,
                                mcc = id.mccString?.toIntOrNull(),
                                mnc = id.mncString?.toIntOrNull(),
                                ssRsrp = ss.ssRsrp,
                                ssRsrq = ss.ssRsrq,
                                ssSinr = ss.ssSinr
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            // Нет разрешения READ_PHONE_STATE
        }

        val trafficInfo = trafficCollector.getTrafficInfo()
        return MeasurementDto(
            location = locDto,
            lteCells = lteCells,
            gsmCells = gsmCells,
            nrCells = nrCells,
            traffic = trafficInfo
        )
    }

    private fun sendToServer(measurement: MeasurementDto) {
        Thread {
            try {
                val json = gson.toJson(measurement)
                val context = ZContext()
                val socket = context.createSocket(SocketType.REQ)
                socket.receiveTimeOut = 5000
                val address = "tcp://${settingsManager.getServerIp()}:${settingsManager.getServerPort()}"
                socket.connect(address)
                socket.send(json.toByteArray(ZMQ.CHARSET), 0)
                val reply = socket.recv(0)
                if (reply != null) {
                    packetsSent++
                    lastSendTime = System.currentTimeMillis()
                    handler.post {
                        tvPacketsSent.text = "Отправлено: $packetsSent"
                        tvLastSendTime.text = "Последняя: ${dateFormat.format(Date(lastSendTime))}"
                        updateConnectionStatus("Соединение установлено ✓", 0x00FF00)
                    }
                }
                socket.close()
                context.close()
            } catch (e: Exception) {
                handler.post {
                    updateConnectionStatus("Ошибка: ${e.message}", 0xFF0000)
                }
            }
        }.start()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSending()
    }
}