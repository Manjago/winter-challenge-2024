import java.util.*
import kotlin.math.abs

data class GridPoint(val x: Int, val y: Int) {
    operator fun plus(other: GridPoint): GridPoint = GridPoint(x + other.x, y + other.y)
    override fun toString(): String {
        return "($x, $y)"
    }
}

val debugEnabled = false
fun debug(v: String) {
    if (debugEnabled) {
        System.err.println(v)
    }
}

class Desk(val width: Int, val height: Int, val allPoints: List<GridPoint>) {

    private fun display(x: Int, y: Int): Char {
        val item = grid[y][x]
        val meOrEnemy = meOrEnemy[y][x]
        return when (item) {
            Item.SPACE -> '.'
            Item.WALL -> '#'
            Item.ROOT -> if (meOrEnemy == MeOrEnemy.ME) 'R' else 'W'
            Item.BASIC -> if (meOrEnemy == MeOrEnemy.ME) '+' else '*'
            Item.A -> 'A'
        }
    }

    fun display(): String {
        val sb = StringBuilder()
        for (y in 0 until height) {
            for (x in 0 until width) {
                sb.append(display(x, y))
            }
            sb.append('\n')
        }
        return sb.toString()
    }


    private enum class Item {
        SPACE, WALL, ROOT, BASIC, A
    }

    private enum class MeOrEnemy {
        UNKNOWN, ME, ENEMY;

        companion object {
            fun byInt(value: Int): MeOrEnemy = when (value) {
                1 -> ME
                0 -> ENEMY
                else -> UNKNOWN
            }
        }
    }

    private val grid: Array<Array<Item>> = Array(height) { Array(width) { Item.SPACE } }
    private val meOrEnemy: Array<Array<MeOrEnemy>> =
        Array(height) { Array(width) { MeOrEnemy.UNKNOWN } }
    private val organIds: Array<Array<Int>> = Array(height) { Array(width) { 0 } }

    var aStock: Int = 0

    fun fill(x: Int, y: Int, type: String, owner: Int, organId: Int) {
        when (type) {
            "WALL" -> grid[y][x] = Item.WALL
            "ROOT" -> {
                grid[y][x] = Item.ROOT
                meOrEnemy[y][x] = MeOrEnemy.byInt(owner)
                organIds[y][x] = organId
            }

            "BASIC" -> {
                grid[y][x] = Item.BASIC
                meOrEnemy[y][x] = MeOrEnemy.byInt(owner)
                organIds[y][x] = organId
            }

            "A" -> grid[y][x] = Item.A
        }
    }

    fun getMyOrgans(): Sequence<GridPoint> = allPoints.asSequence().filter { isOrgan(it) && isMy(it) }

    fun isA(point: GridPoint): Boolean = grid[point.y][point.x] == Item.A
    fun isSpace(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPACE
    fun isOrgan(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT || grid[point.y][point.x] == Item.BASIC
    fun isMy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ME
    fun organId(point: GridPoint): Int = organIds[point.y][point.x]
    fun inbound(point: GridPoint) = point.x in 0 until grid[0].size && point.y in 0 until grid.size

}

class Logic {

    lateinit var desk: Desk

    fun GridPoint.neighbours(): List<GridPoint> {
        val result = mutableListOf<GridPoint>()
        for (dir in directions) {
            val pretender = this + dir
            if (desk.inbound(pretender)) {
                result.add(GridPoint(pretender.x, pretender.y))
            }
        }
        return result
    }

    fun GridPoint.getNearestA(): GridPoint? {
        val queue = ArrayDeque<GridPoint>()
        queue.add(this)
        val seen = mutableSetOf<GridPoint>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (seen.contains(current)) {
                continue
            }
            seen += current

            if (desk.isA(current)) {
                return current
            }

            current.neighbours().asSequence().filter { desk.isSpace(it) || desk.isA(it) }.forEach { neighbour ->
                queue.add(neighbour)
            }

        }
        return null
    }

    fun dist(a: GridPoint, b: GridPoint): Int = abs(a.x - b.x) + abs(a.y - b.y)

    fun move(): String {

        data class FromTo(val from: GridPoint, val to: GridPoint, val dist: Int)

        val all = mutableListOf<FromTo>()

        val myOrgans = desk.getMyOrgans()
        for (myOrgan in myOrgans) {
            debug("Find for organ $myOrgan")
            val pretenderA = myOrgan.getNearestA()
            if (pretenderA != null) {
                val dist = dist(myOrgan, pretenderA)
                val element = FromTo(myOrgan, pretenderA, dist)
                debug("Pretender found $element")
                all.add(element)
            }
        }

        if (all.isEmpty()) {
            return "WAIT"
        }

        val move: FromTo? = all.minByOrNull { it.dist }
        return if (move == null) {
            "WAIT"
        } else {
            val organId = desk.organId(move.from)
            val xTo = move.to.x
            val yTo = move.to.y
            "GROW $organId $xTo $yTo BASIC"
        }
    }

    companion object {
        private val NORTH = GridPoint(0, -1)
        private val SOUTH = GridPoint(0, 1)
        private val WEST = GridPoint(-1, 0)
        private val EAST = GridPoint(1, 0)

        private val directions = listOf(
            NORTH, SOUTH, EAST, WEST
        )
    }

}

fun main() {
    val logic = Logic()
    val input = Scanner(System.`in`)
    val width = input.nextInt() // columns in the game grid
    val height = input.nextInt() // rows in the game grid


    val allPoints = mutableListOf<GridPoint>()
    for(y in 0 until height) {
        for(x in 0 until width) {
            allPoints.add(GridPoint(x, y))
        }
    }

    // game loop
    while (true) {
        val desk = Desk(width, height, allPoints)
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

            desk.fill(x, y, type, owner, organId)

        }
        val myA = input.nextInt()
        desk.aStock = myA
        val myB = input.nextInt()
        val myC = input.nextInt()
        val myD = input.nextInt() // your protein stock
        val oppA = input.nextInt()
        val oppB = input.nextInt()
        val oppC = input.nextInt()
        val oppD = input.nextInt() // opponent's protein stock
        val requiredActionsCount =
            input.nextInt() // your number of organisms, output an action for each one in any order
        //System.err.println(display())
        logic.desk = desk
        for (i in 0 until requiredActionsCount) {

            // Write an action using println()
            // To debug: System.err.println("Debug messages...");

            val move = logic.move()
            println(move)
        }
    }
}


