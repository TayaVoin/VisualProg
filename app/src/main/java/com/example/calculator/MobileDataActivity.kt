package com.example.calculator

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.android.gms.location.*  // FusedLocationProvider [web:156][web:159]
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import android.util.Log


class MobileDataActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    private val gson: Gson = GsonBuilder().create()
    private val handler = Handler(Looper.getMainLooper())

    private var sending = false

    private val serverIp = "192.168.0.105"   // например 192.168.0.105
    private val serverPort = 50123           // тот же порт в server_zmq.py

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_data)

        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStartSending)
        btnStop = findViewById(R.id.btnStopSending)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(1000L)   // 1 секунда [web:165]
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        btnStart.setOnClickListener {
            checkPermissionsAndStart()
        }

        btnStop.setOnClickListener {
            sending = false
            tvStatus.text = "Stopped"
        }
    }

    private fun checkPermissionsAndStart() {
        val need = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        val notGranted = need.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 200)
        } else {
            startSending()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startSending()
        } else {
            tvStatus.text = "Permissions denied"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSending() {
        sending = true
        tvStatus.text = "Sending every 1 sec..."

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!sending) return
                    val loc = result.lastLocation ?: return
                    val dto = buildMeasurement(loc)
                    sendToServer(dto)
                }
            },
            Looper.getMainLooper()
        )
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

        var lteDto: CellInfoLteDto? = null
        var gsmDto: CellInfoGsmDto? = null
        var nrDto: CellInfoNrDto? = null

        val allCells = telephonyManager.allCellInfo  // может вернуть null/пусто [web:155][web:161]
        if (allCells != null) {
            for (info in allCells) {
                when (info) {
                    is CellInfoLte -> {
                        val id = info.cellIdentity
                        val ss = info.cellSignalStrength
                        lteDto = CellInfoLteDto(
                            band = id.bandwidth,
                            earfcn = id.earfcn,
                            mcc = id.mcc,
                            mnc = id.mnc,
                            pci = id.pci,
                            tac = id.tac,
                            rsrp = ss.rsrp,
                            rsrq = ss.rsrq,
                            rssnr = ss.rssnr,
                            cqi = ss.cqi,
                            timingAdvance = ss.timingAdvance
                        )
                    }
                    is CellInfoGsm -> {
                        val id = info.cellIdentity
                        val ss = info.cellSignalStrength
                        gsmDto = CellInfoGsmDto(
                            cid = id.cid,
                            lac = id.lac,
                            mcc = id.mcc,
                            mnc = id.mnc,
                            bsic = id.bsic,
                            arfcn = id.arfcn,
                            rssi = ss.dbm,
                            timingAdvance = ss.timingAdvance
                        )
                    }
                    is CellInfoNr -> {
                        val id = info.cellIdentity as CellIdentityNr
                        val ss = info.cellSignalStrength as CellSignalStrengthNr
                        nrDto = CellInfoNrDto(
                            nci = id.nci,
                            nrarfcn = id.nrarfcn,
                            tac = id.tac,
                            mcc = id.mccString?.toIntOrNull(),
                            mnc = id.mncString?.toIntOrNull(),
                            ssRsrp = ss.ssRsrp,
                            ssRsrq = ss.ssRsrq,
                            ssSinr = ss.ssSinr
                        )
                    }
                }
            }
        }

        return MeasurementDto(
            location = locDto,
            lte = lteDto,
            gsm = gsmDto,
            nr = nrDto
        )
    }

    private fun sendToServer(measurement: MeasurementDto) {
        val json = gson.toJson(measurement)
        Log.d("ZMQ_JSON", "Prepared JSON: ${json.take(120)}")

        Thread {
            try {
                val context = ZContext()
                val socket = context.createSocket(SocketType.REQ)
                Log.d("ZMQ_JSON", "Connecting to tcp://$serverIp:$serverPort")
                socket.connect("tcp://$serverIp:$serverPort")

                socket.send(json.toByteArray(ZMQ.CHARSET), 0)
                Log.d("ZMQ_JSON", "JSON sent, waiting reply")

                val reply = socket.recv(0)
                Log.d("ZMQ_JSON", "Reply: ${String(reply, ZMQ.CHARSET)}")

                handler.post {
                    tvStatus.text = "Last sent: ${System.currentTimeMillis()}"
                }

                socket.close()
                context.close()
            } catch (e: Exception) {
                Log.e("ZMQ_JSON", "Error: ${e.message}", e)
                handler.post { tvStatus.text = "Send error: ${e.message}" }
            }
        }.start()
    }
}
