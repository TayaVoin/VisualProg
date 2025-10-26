package classes

import kotlin.collections.plusAssign
import interfaces.Movable
import kotlin.random.Random

class Driver(
    fullName: String,
    age: Int,
    currentSpeed: Double,
    var Category: String,
    var Car: String,
) : Human(fullName, age, currentSpeed), Movable {
    override fun move() {
        x += Random.nextDouble(currentSpeed)
        println("Водитель ${fullName} ${age} лет едет на автомобиле марки ${Car} со скоростью ${currentSpeed} и обладает правами категории ${Category}, текущая позиция ${"%.1f".format(x)}")
    }
}