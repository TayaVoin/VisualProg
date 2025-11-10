import kotlin.random.Random

open class Human(
    var fullName: String,
    override var age: Int,
    override var currentSpeed: Double
) : Movable, Runnable {
    override var x: Double = 0.0
    override var y: Double = 0.0

    override fun move() {
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