import kotlin.random.Random

// Класс Human с свойствами ФИО, возраст, текущая скорость
class Human(
    var fullName: String,
    var age: Int,
    var currentSpeed: Double
) {
    // Координаты для моделирования движения в 2D (x, y)
    var x: Double = 0.0
    var y: Double = 0.0

    // Метод move реализует случайное блуждание (Random Walk)
    fun move() {
        // Случайный шаг по оси X и Y: скорость в произвольном направлении
        val dx = Random.nextDouble(-currentSpeed, currentSpeed)
        val dy = Random.nextDouble(-currentSpeed, currentSpeed)
        x += dx
        y += dy
        println("$fullName переместился на координаты (%.2f, %.2f)".format(x, y))
    }

    // Геттеры и сеттеры автоматически создаются в Kotlin для var свойств
}

// Главная функция для запуска симуляции
fun main() {
    val numberOfHumans = 3 // Например, количество зависит от номера в группе
    val simulationTimeSeconds = 5 // Продолжительность симуляции (секунды)
    val humans = Array(numberOfHumans) { index ->
        Human("Человек №${index + 1}", 20 + index, currentSpeed = 1.0 + index.toDouble())
    }

    // Цикл симуляции
    for (t in 1..simulationTimeSeconds) {
        println("Время: $t секунда")
        for (human in humans) {
            human.move()
        }
        Thread.sleep(1000) // Задержка 1 секунда между шагами симуляции
    }
}
