package com.example.calculator
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.content.Context
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class ZaglushkaButton(
    private val button: Button
) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        GoToCalcBTN = findViewById(R.id.GoToCalc)
        GoToCalcBTN.setOnClickListener({
            val randomIntent = Intent(this, CalculatorActivity::class.java)
            startActivity(randomIntent)
        });
        listOf(
            R.id.GoToGeo, R.id.GoToPlayer, R.id.GoToMobileData
        ).forEach { id ->
            ZaglushkaButton(findViewById(id))
        }
    }
}