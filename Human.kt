import kotlin.random.Random

open class Human(
    var fullName: String,
    var age: Int,
    var currentSpeed: Double
) : Runnable {
    var x: Double = 0.0
    var y: Double = 0.0

    open fun move() {
        val dx = Random.nextDouble(-currentSpeed, currentSpeed)
        val dy = Random.nextDouble(-currentSpeed, currentSpeed)
        x += dx
        y += dy
        println("$fullName переместился на координаты (%.2f, %.2f)".format(x, y))
    }

    override fun run() {
        repeat(5) {
            move()
            Thread.sleep(1000)
        }
    }
}

// Класс-наследник Driver, движется по прямой (ось X)
class Driver(
    fullName: String,
    age: Int,
    currentSpeed: Double
) : Human(fullName, age, currentSpeed) {

    override fun move() {
        x += currentSpeed
        println("Водитель $fullName переместился на координату X = %.2f".format(x))
    }
}

fun main() {
    // Создаём несколько Human и одного Driver
    val humans = listOf(
        Human("Человек 1", 25, 1.2),
        Human("Человек 2", 20, 1.5),
        Human("Человек 3", 28, 1.7)
    )
    val driver = Driver("Водитель", 40, 2.0)

    // Запускаем параллельное движение через потоки
    val threads = humans.map { Thread(it) } + Thread(driver)
    threads.forEach { it.start() }
    threads.forEach { it.join() }
}