import java.util.*

fun main() {
    val input = Scanner(System.`in`)
    val width = input.nextInt() // columns in the game grid
    val height = input.nextInt() // rows in the game grid

    val brain = Brain(width, height)
    // game loop
    while (true) {
        brain.clear()
        val entityCount = input.nextInt()
        for (i in 0 until entityCount) {
            val x = input.nextInt()
            val y = input.nextInt() // grid coordinate
            val type = input.next() // WALL, ROOT, BASIC, TENTACLE, HARVESTER, SPORER, A, B, C, D
            val owner = input.nextInt() // 1 if your organ, 0 if enemy organ, -1 if neither
            val organId = input.nextInt() // id of this entity if it's an organ, 0 otherwise
            val organDir = input.next() // N,E,S,W or X if not an organ
            val organParentId = input.nextInt()
            val organRootId = input.nextInt()

            when {
                type == "A" -> brain.proteinA.add(GridPoint(x, y))
                owner == 1 && (type == "ROOT" || type == "BASIC") -> brain.organ.add(GridPoint(x, y))
            }

        }
        val myA = input.nextInt()
        val myB = input.nextInt()
        val myC = input.nextInt()
        val myD = input.nextInt() // your protein stock
        val oppA = input.nextInt()
        val oppB = input.nextInt()
        val oppC = input.nextInt()
        val oppD = input.nextInt() // opponent's protein stock
        val requiredActionsCount =
            input.nextInt() // your number of organisms, output an action for each one in any order
        System.err.println(brain)
        for (i in 0 until requiredActionsCount) {

            // Write an action using println()
            // To debug: System.err.println("Debug messages...");

            println("WAIT")
        }
    }
}


data class GridPoint(val x: Int, val y: Int)

class Brain(
    val width: Int, val height: Int,
    val proteinA: MutableList<GridPoint> = arrayListOf(),
    val organ: MutableList<GridPoint> = arrayListOf(),
) {

    fun clear() {
        proteinA.clear()
        organ.clear()
    }

    override fun toString(): String {
        return "A=${proteinA.size},o=${organ.size}"
    }
}