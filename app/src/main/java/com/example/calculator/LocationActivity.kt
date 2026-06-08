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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder

class LocationActivity : AppCompatActivity() {

    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnGetLocation: Button

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var telephonyManager: TelephonyManager
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)

        tvLatitude = findViewById(R.id.tv_latitude)
        tvLongitude = findViewById(R.id.tv_longitude)
        tvAltitude = findViewById(R.id.tv_altitude)
        tvTime = findViewById(R.id.tv_time)
        btnGetLocation = findViewById(R.id.btn_get_location)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        btnGetLocation.setOnClickListener {
            checkPermissionsAndGetLocation()
        }
    }

    private fun checkPermissionsAndGetLocation() {
        val need = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        val notGranted = need.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        } else {
            getLocationAndDisplay()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            getLocationAndDisplay()
        } else {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndDisplay() {
        val locationRequest = LocationRequest.Builder(1000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation
                    if (loc != null) {
                        displayLocation(loc)
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            Looper.getMainLooper()
        )
    }

    private fun displayLocation(location: Location) {
        tvLatitude.text = "Latitude: ${location.latitude}"
        tvLongitude.text = "Longitude: ${location.longitude}"
        tvAltitude.text = "Altitude: ${location.altitude} m"
        tvTime.text = "Time: ${System.currentTimeMillis()}"

        val measurement = buildMeasurement(location)
        val json = gson.toJson(measurement)
        android.util.Log.d("LocationActivity", "Measurement: $json")
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
                        lteCells.add(
                            CellInfoLteDto(
                                band = id.bandwidth,
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
        } catch (e: SecurityException) {
            android.util.Log.e("LocationActivity", "Cell info error", e)
        }

        // Заглушка для traffic (так как в LocationActivity он не используется)
        val dummyTraffic = TrafficInfo(0, 0, emptyList())

        return MeasurementDto(
            location = locDto,
            lteCells = lteCells,
            gsmCells = gsmCells,
            nrCells = nrCells,
            traffic = dummyTraffic
        )
    }
}