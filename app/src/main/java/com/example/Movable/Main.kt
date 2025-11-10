import interfaces.Movable
import classes.Human
import classes.Driver
fun main() {
    val movables: List<Movable> = listOf(
        Human("Иван Иванов", 25, 2.5),
        Human("Петр Петров", 30, 3.0),
        Human("Анна Сидорова", 22, 1.8),
        Driver("Иван Иванов", 25, 110.0, "B", "Mercedes")
    )

    movables.forEach { movable ->
        Thread {
            repeat(10) {
                movable.move()
                Thread.sleep(1000)
            }
        }.start()
    }
}
