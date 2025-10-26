package classes

import kotlin.random.Random
import interfaces.Movable



open class Human(
    var fullName: String,
    age: Int,
    override var currentSpeed: Double
) : Movable {

    override var age: Int = age
        set(value) {
            if (value >= 0) field = value
            else println("Возраст не может быть отрицательным!")
        }

    override var x: Double = 0.0
    override var y: Double = 0.0

    override fun move() {
        x += Random.nextDouble(-currentSpeed, currentSpeed)
        y += Random.nextDouble(-currentSpeed, currentSpeed)
        println("${fullName} ${age} лет: (${"%.1f".format(x)}, ${"%.1f".format(y)})")
    }
}