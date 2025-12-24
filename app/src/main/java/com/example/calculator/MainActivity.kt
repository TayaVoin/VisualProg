package com.example.calculator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val goToCalcBtn = findViewById<Button>(R.id.GoToCalc)
        goToCalcBtn.setOnClickListener {
            startActivity(Intent(this, CalculatorActivity::class.java))
        }

        val goToPlayerBtn = findViewById<Button>(R.id.GoToPlayer)
        goToPlayerBtn.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }

        val goToGeoBtn = findViewById<Button>(R.id.GoToGeo)
        goToGeoBtn.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        val goToMobileDataBtn = findViewById<Button>(R.id.GoToMobileData)
        goToMobileDataBtn.setOnClickListener {
            val message = "Функционал Mobile Network Data временно недоступен :("
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        val goToSocketsBtn = findViewById<Button>(R.id.GoToSockets)
        goToSocketsBtn.setOnClickListener {
            startActivity(Intent(this, SocketsActivity::class.java))
        }
        val goToMobileJsonBtn = findViewById<Button>(R.id.GoToMobileJson)
        goToMobileJsonBtn.setOnClickListener {
            startActivity(Intent(this, MobileDataActivity::class.java))
        }
    }
}
