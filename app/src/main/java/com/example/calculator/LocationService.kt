package com.example.calculator

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.telephony.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var trafficCollector: TrafficCollector
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val executor = Executors.newSingleThreadExecutor()
    private var isRunning = false
    private var locationCallback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "LocationServiceChannel"
        const val TAG = "LocationService"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            settingsManager = SettingsManager(this)
            trafficCollector = TrafficCollector(this)
            createNotificationChannel()
            startForeground(1, createNotification())
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            startLocationUpdates()
        } catch (e: Exception) {
            logError("onCreate", e)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Сбор данных о сети")
            .setContentText("Отправка на ${settingsManager.getServerIp()}:${settingsManager.getServerPort()}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Обновляем уведомление при изменении настроек
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification())
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            showToastAndLog("Нет разрешения на геолокацию. Сервис остановлен.")
            stopSelf()
            return
        }
        isRunning = true
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isRunning) return
                val location = result.lastLocation ?: return
                try {
                    val measurement = buildMeasurement(location)
                    sendToServer(measurement)
                } catch (e: Exception) {
                    logError("onLocationResult", e)
                }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback!!, null
            )
        } catch (e: SecurityException) {
            showToastAndLog("Ошибка доступа к геолокации: ${e.message}")
            stopSelf()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val allCells = telephonyManager.allCellInfo
                allCells?.forEach { info ->
                    when (info) {
                        is CellInfoLte -> {
                            val id = info.cellIdentity
                            val ss = info.cellSignalStrength
                            val band = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                id.bands.firstOrNull()
                            } else {
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
                                    timingAdvance = ss.timingAdvance,
                                    rssi = ss.rssi,
                                    registered = info.isRegistered
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
            }
        } catch (e: SecurityException) {
            logError("buildMeasurement - cell info", e)
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
        executor.execute {
            try {
                val ip = settingsManager.getServerIp()
                val port = settingsManager.getServerPort()
                val json = gson.toJson(measurement)
                val context = ZContext()
                val socket = context.createSocket(SocketType.REQ)
                socket.receiveTimeOut = 5000
                socket.connect("tcp://$ip:$port")
                socket.send(json.toByteArray(ZMQ.CHARSET), 0)
                socket.recv(0) // ждём ответ, но не обрабатываем
                socket.close()
                context.close()
            } catch (e: Exception) {
                logError("sendToServer", e)
            }
        }
    }

    private fun showToastAndLog(message: String) {
        android.os.Handler(mainLooper).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
        logError("Service", Exception(message))
    }

    private fun logError(context: String, e: Exception) {
        try {
            val stackTrace = android.util.Log.getStackTraceString(e)
            val logMessage = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} [$context] $stackTrace\n"
            val file = File(getExternalFilesDir(null), "location_service_error.txt")
            file.appendText(logMessage)
            android.util.Log.e(TAG, "$context: ${e.message}", e)
        } catch (logError: Exception) {
            android.util.Log.e(TAG, "Failed to write log", logError)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        executor.shutdown()
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                logError("onDestroy", e)
            }
        }
    }
}