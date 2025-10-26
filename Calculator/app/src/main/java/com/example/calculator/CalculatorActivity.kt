package com.example.calculator

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class CalcButton(
    private val button: Button,
    private val mainText: TextView,
) {
    init {
        if (button.text == "AC"){
            button.setOnClickListener {
                mainText.text = ""
            }
        }
        else if (button.text == "⌫"){
            button.setOnClickListener {
                val str = mainText.text.toString()
                if(str.isNotEmpty()){
                    mainText.text = str.substring(0, str.length - 1)
                }
            }
        }
        else if (button.text == "="){
            button.setOnClickListener {
                mainText.text = calculate(mainText.text.toString())
            }
        }
        else{button.setOnClickListener {
            mainText.append(button.text)
        }}

    }
}


private fun calculate(expression: String): String {
    try {
        val numbers = mutableListOf<Double>()
        val operators = mutableListOf<Char>()
        var currentNumber = ""
        if (expression[0]=='-' || expression[0]=='+'){
            currentNumber = "0"
        }

        for (i in expression.indices) {
            val char = expression[i]

            if (char.isDigit() || char == '.') {
                currentNumber += char
            }
            if ((!char.isDigit() && char != '.') || i == expression.length - 1) {
                numbers.add(currentNumber.toDouble())
                currentNumber = ""
                if (char == '+' || char == '-' || char == 'x' || char == '÷') {
                    operators.add(char)

                }
            }
        }
        var i = 0
        while (i < operators.size) {
            val op = operators[i]
            if (op == 'x' || op == '÷') {
                val a = numbers.getOrNull(i)
                val b = numbers.getOrNull(i + 1)
                if (a == null || b == null) {
                    return "Недостаточно операндов"
                }
                val res = when (op) {
                    'x' -> a * b
                    '÷' -> if (b != 0.0) a / b else return "Деление на 0"
                    else -> 0.0
                }
                numbers[i] = res
                numbers.removeAt(i + 1)
                operators.removeAt(i)
            } else {
                i++
            }
        }
        var result = numbers.firstOrNull() ?: return "Ошибка: нет чисел"
        for (j in operators.indices) {
            val op = operators[j]
            val b = numbers.getOrNull(j + 1) ?: continue
            when (op) {
                '+' -> result += b
                '-' -> result -= b
            }
        }

        return if (result % 1 == 0.0) {
            result.toInt().toString()
        } else {
            result.toString()
        }

    } catch (e: Exception) {
        return "Error"
    }
}


class CalculatorActivity : AppCompatActivity() {
    private lateinit var mainText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_claculator)
        mainText = findViewById(R.id.main_text)

        listOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9,
            R.id.btn_plus, R.id.btn_minus, R.id.btn_umnozenie,
            R.id.delenie, R.id.btn_zapyataya,
            R.id.AC, R.id.btn_delete,R.id.btn_ravno
        ).forEach { id ->
            CalcButton(findViewById(id), mainText)
        }
    }
}