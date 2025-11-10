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
