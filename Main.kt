fun main() {
    val humans = listOf(
        Human("Человек 1", 25, 1.2),
        Human("Человек 2", 20, 1.5),
        Human("Человек 3", 28, 1.7)
    )
    val driver = Driver("Водитель", 40, 2.0)

    val threads = humans.map { Thread(it) } + Thread(driver)
    threads.forEach { it.start() }
    threads.forEach { it.join() }
}
