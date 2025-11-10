package com.example.calculator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class ZaglushkaButton(private val button: Button) {
    init {
        button.setOnClickListener {
            val context = button.context
            val message = "Функционал ${button.text} временно недоступен :("
            val duration = Toast.LENGTH_SHORT
            Toast.makeText(context, message, duration).show()
        }
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var GoToCalcBTN: Button
    private lateinit var GoToPlayerBTN: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        GoToCalcBTN = findViewById(R.id.GoToCalc)
        GoToCalcBTN.setOnClickListener {
            val calcIntent = Intent(this, CalculatorActivity::class.java)
            startActivity(calcIntent)
        }

        GoToPlayerBTN = findViewById(R.id.GoToPlayer)
        GoToPlayerBTN.setOnClickListener {
            val playerIntent = Intent(this, PlayerActivity::class.java)
            startActivity(playerIntent)
        }

        listOf(
            R.id.GoToGeo, R.id.GoToMobileData
        ).forEach { id ->
            ZaglushkaButton(findViewById(id))
        }
    }
}
