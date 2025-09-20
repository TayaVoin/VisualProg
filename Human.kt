import kotlin.random.Random

// Класс Human с свойствами ФИО, возраст, текущая скорость
open class Human(
    var fullName: String,
    var age: Int,
    var currentSpeed: Double
) : Runnable { // Реализуем Runnable для работы в потоке

    // Координаты для моделирования движения в 2D (x, y)
    var x: Double = 0.0
    var y: Double = 0.0

    // Метод move реализует случайное блуждание (Random Walk)
    open fun move() {
        val dx = Random.nextDouble(-currentSpeed, currentSpeed)
        val dy = Random.nextDouble(-currentSpeed, currentSpeed)
        x += dx
        y += dy
        println("$fullName переместился на координаты (%.2f, %.2f)".format(x, y))
    }

    // Метод run для потока
    override fun run() {
        repeat(5) {
            move()
            Thread.sleep(1000)
        }
    }
}

// Подкласс Driver, движется только по прямой (ось X)
class Driver(
    fullName: String,
    age: Int,
    currentSpeed: Double
) : Human(fullName, age, currentSpeed) {

    override fun move() {
        // Движение только по X
        x += currentSpeed
        println("Водитель $fullName переместился на координату X = %.2f".format(x))
    }
}

// Главная функция для запуска симуляции
fun main() {
    val human = Human("Обычный человек", 30, 1.5)
    val driver = Driver("Водитель", 40, 2.0)

    val threadHuman = Thread(human)
    val threadDriver = Thread(driver)

    threadHuman.start()
    threadDriver.start()

    threadHuman.join()
    threadDriver.join()
}
