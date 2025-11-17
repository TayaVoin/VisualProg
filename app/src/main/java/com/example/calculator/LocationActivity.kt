package com.example.calculator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LocationActivity : AppCompatActivity() {
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnGetLocation: Button

    data class LocationEntry(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val time: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)

        tvLatitude = findViewById(R.id.tv_latitude)
        tvLongitude = findViewById(R.id.tv_longitude)
        tvAltitude = findViewById(R.id.tv_altitude)
        tvTime = findViewById(R.id.tv_time)
        btnGetLocation = findViewById(R.id.btn_get_location)

        btnGetLocation.setOnClickListener {
            requestLocation()
        }
    }

    private fun requestLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1234
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                tvLatitude.text = getString(R.string.latitude_placeholder, location.latitude)
                tvLongitude.text = getString(R.string.longitude_placeholder, location.longitude)
                tvAltitude.text = getString(R.string.altitude_placeholder, location.altitude)
                tvTime.text = getString(R.string.current_time_placeholder, location.time.toString())

                val entry = LocationEntry(
                    location.latitude,
                    location.longitude,
                    location.altitude,
                    location.time.toString()
                )
                saveLocation(entry)
            } else {
                tvLatitude.text = getString(R.string.location_na)
                tvLongitude.text = getString(R.string.location_na)
                tvAltitude.text = getString(R.string.location_na)
                tvTime.text = getString(R.string.location_na)
            }
        }.addOnFailureListener {
            tvLatitude.text = getString(R.string.location_error)
            tvLongitude.text = getString(R.string.location_error)
            tvAltitude.text = getString(R.string.location_error)
            tvTime.text = getString(R.string.location_error)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1234 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocation()
        }
    }

    private fun saveLocation(entry: LocationEntry) {
        val jsonStr = """
        {
            "latitude": ${entry.latitude},
            "longitude": ${entry.longitude},
            "altitude": ${entry.altitude},
            "time": "${entry.time}"
        }
        """.trimIndent()
        // внешний storage для user-файлов приложения (без дополнительных разрешений)
        val file = File(getExternalFilesDir(null), "location_log.json")
        file.appendText(jsonStr + "\n")
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
}
