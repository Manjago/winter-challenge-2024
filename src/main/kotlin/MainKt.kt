import Item.*
import java.util.*
import kotlin.math.abs

data class GridPoint(val x: Int, val y: Int) {
    operator fun plus(other: GridPoint): GridPoint = GridPoint(x + other.x, y + other.y)
}

val NORTH = GridPoint(0, -1)
val SOUTH = GridPoint(0, 1)
val WEST = GridPoint(-1, 0)
val EAST = GridPoint(1, 0)

val directions = listOf(
    NORTH, SOUTH, EAST, WEST
)

lateinit var allPoints: MutableList<GridPoint>

enum class Item {
  SPACE, WALL, ROOT, BASIC, A
}

enum class MeOrEnemy {
  UNKNOWN, ME, ENEMY;
  companion object {
      fun byInt(value: Int): MeOrEnemy = when(value) {
          1 -> ME
          0 -> ENEMY
          else -> UNKNOWN
      }
  }
}

fun display(x: Int, y: Int): Char {
    val item = grid[y][x]
    val meOrEnemy = meOrEnemy[y][x]
    return when (item) {
        SPACE -> '.'
        WALL -> '#'
        ROOT -> if (meOrEnemy == MeOrEnemy.ME) 'R' else 'W'
        BASIC -> if (meOrEnemy == MeOrEnemy.ME) '+' else '*'
        A -> 'A'
    }
}

lateinit var grid: Array<Array<Item>>
lateinit var meOrEnemy: Array<Array<MeOrEnemy>>
lateinit var organIds: Array<Array<Int>>
var aStock: Int = 0

fun display(): String {
    val sb = StringBuilder()
    for(y in 0 until grid.size) {
        for(x in 0 until grid[0].size) {
            sb.append(display(x,y))
        }
        sb.append('\n')
    }
    return sb.toString()
}

fun getMyOrgans(): Sequence<GridPoint> = allPoints.asSequence().filter { it.isOrgan() && it.isMy() }

fun GridPoint.isA(): Boolean = grid[y][x] == Item.A
fun GridPoint.isSpace(): Boolean = grid[y][x] == Item.SPACE
fun GridPoint.isOrgan(): Boolean = grid[y][x] == Item.ROOT || grid[y][x] == Item.BASIC
fun GridPoint.isMy(): Boolean = meOrEnemy[y][x] == MeOrEnemy.ME
fun GridPoint.organId(): Int = organIds[y][x]

fun GridPoint.inbound() = x in 0 until grid[0].size && y in 0 until grid.size

fun GridPoint.neighbours(): List<GridPoint> {
    val result = mutableListOf<GridPoint>()
    for(dir in directions) {
        val pretender = this + dir
        if (pretender.inbound()){
           result.add(GridPoint(pretender.x, pretender.y))
        }
    }
    return result
}

fun GridPoint.getNearestA() : GridPoint? {
    val queue = ArrayDeque<GridPoint>()
    queue.add(this)
    val seen = mutableSetOf<GridPoint>()
    while(queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (seen.contains(current)) {
            continue
        }
        seen.add(current)

        if (current.isA()) {
            return current
        }

        current.neighbours().asSequence().filter { it.isSpace() || it.isA()}.forEach { neighbour ->
            queue.add(neighbour)
        }

    }
    return null
}

fun dist(a: GridPoint, b: GridPoint): Int = abs(a.x - b.x) + abs(a.y - b.y)

fun move(): String {


    data class FromTo(val from: GridPoint, val to: GridPoint, val dist: Int)
    val all = mutableListOf<FromTo>()

    val myOrgans = getMyOrgans()
    for(myOrgan in myOrgans) {
        val pretenderA = myOrgan.getNearestA()
        if (pretenderA != null) {
            val dist = dist(myOrgan, pretenderA)
            all.add(FromTo(myOrgan, pretenderA, dist))
        }
    }

    if (all.isEmpty()) {
        return "WAIT"
    }

    val move: FromTo? = all.asSequence().sortedBy { it.dist }.firstOrNull()
    return if (move == null) {
        "WAIT"
    } else {
        val organId = move.from.organId()
        val xTo = move.to.x
        val yTo = move.to.y
        "GROW $organId $xTo $yTo"
    }
}

fun main() {
    val input = Scanner(System.`in`)
    val width = input.nextInt() // columns in the game grid
    val height = input.nextInt() // rows in the game grid

    grid = Array<Array<Item>>(height) {Array<Item>(width) {SPACE} }
    meOrEnemy = Array<Array<MeOrEnemy>>(height) {Array<MeOrEnemy>(width) {MeOrEnemy.UNKNOWN} }
    organIds = Array<Array<Int>>(height) {Array<Int>(width) {0} }

    for(y in 0 until grid.size) {
        for(x in 0 until grid[0].size) {
            allPoints.add(GridPoint(x, y))
        }
    }

    // game loop
    while (true) {
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

            when (type) {
                "WALL" -> grid[y][x] = WALL
                "ROOT" -> {
                    grid[y][x] = ROOT
                    meOrEnemy[y][x] = MeOrEnemy.byInt(owner)
                    organIds[y][x] = organId
                }
                "BASIC" -> {
                    grid[y][x] = BASIC
                    meOrEnemy[y][x] = MeOrEnemy.byInt(owner)
                    organIds[y][x] = organId
                }
                "A" -> grid[y][x] = A
            }

        }
        val myA = input.nextInt()
        aStock = myA
        val myB = input.nextInt()
        val myC = input.nextInt()
        val myD = input.nextInt() // your protein stock
        val oppA = input.nextInt()
        val oppB = input.nextInt()
        val oppC = input.nextInt()
        val oppD = input.nextInt() // opponent's protein stock
        val requiredActionsCount =
            input.nextInt() // your number of organisms, output an action for each one in any order
        System.err.println(display())
        for (i in 0 until requiredActionsCount) {

            // Write an action using println()
            // To debug: System.err.println("Debug messages...");

            println("WAIT")
        }
    }
}


