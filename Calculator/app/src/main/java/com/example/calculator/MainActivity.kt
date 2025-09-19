package com.example.calculator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var tvDisplay: TextView
    private var operand: String = ""
    private var operator: String = ""
    private var lastNumeric: Boolean = false
    private var stateError: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDisplay = findViewById(R.id.tvDisplay)

        val buttons = listOf(
            Pair(R.id.btn0, "0"), Pair(R.id.btn1, "1"), Pair(R.id.btn2, "2"), Pair(R.id.btn3, "3"),
            Pair(R.id.btn4, "4"), Pair(R.id.btn5, "5"), Pair(R.id.btn6, "6"), Pair(R.id.btn7, "7"),
            Pair(R.id.btn8, "8"), Pair(R.id.btn9, "9")
        )


        buttons.forEach { (id, value) ->
            findViewById<Button>(id).setOnClickListener { appendNumber(value) }
        }


        findViewById<Button>(R.id.btnPlus).setOnClickListener { appendOperator("+") }
        findViewById<Button>(R.id.btnMinus).setOnClickListener { appendOperator("-") }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { appendOperator("*") }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { appendOperator("/") }

        // Обработка кнопки "="
        findViewById<Button>(R.id.btnEquals).setOnClickListener { calculateResult() }
    }

    private fun appendNumber(number: String) {
        if (stateError) {
            tvDisplay.text = number
            stateError = false
        } else {
            if (tvDisplay.text == "0") {
                tvDisplay.text = number
            } else {
                tvDisplay.append(number)
            }
        }
        lastNumeric = true
    }

    private fun appendOperator(op: String) {
        if (lastNumeric && !stateError && operator.isEmpty()) {
            operand = tvDisplay.text.toString()
            tvDisplay.append(op)
            operator = op
            lastNumeric = false
        }
    }

    private fun calculateResult() {
        if (operator.isNotEmpty() && !stateError && lastNumeric) {
            val parts = tvDisplay.text.split(operator)
            if (parts.size == 2) {
                val first = parts[0].toDoubleOrNull()
                val second = parts[1].toDoubleOrNull()
                if (first != null && second != null) {
                    val result = when (operator) {
                        "+" -> first + second
                        "-" -> first - second
                        "*" -> first * second
                        "/" -> if (second != 0.0) first / second else "Ошибка"
                        else -> "Ошибка"
                    }
                    tvDisplay.text = result.toString()
                } else {
                    tvDisplay.text = "Ошибка"
                    stateError = true
                }
            } else {
                tvDisplay.text = "Ошибка"
                stateError = true
            }
            operator = ""
        }
    }
}
