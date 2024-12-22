import java.util.*
import javax.lang.model.util.Elements
import kotlin.math.abs

data class GridPoint(val x: Int, val y: Int) {
    operator fun plus(other: GridPoint): GridPoint = GridPoint(x + other.x, y + other.y)
    operator fun minus(other: GridPoint): GridPoint = GridPoint(x - other.x, y - other.y)
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
            Item.ROOT -> if (meOrEnemy == MeOrEnemy.ME) 'R' else 'r'
            Item.BASIC -> if (meOrEnemy == MeOrEnemy.ME) '+' else '*'
            Item.A -> 'A'
            Item.HARVESTER -> if (meOrEnemy == MeOrEnemy.ME) 'H' else 'h'
            Item.TENTACLE -> if (meOrEnemy == MeOrEnemy.ME) 'T' else 't'
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
        SPACE, WALL, ROOT, BASIC, A, HARVESTER, TENTACLE
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

        fun registerOrgan() {
            meOrEnemy[y][x] = MeOrEnemy.byInt(owner)
            organIds[y][x] = organId
        }

        when (type) {
            "WALL" -> grid[y][x] = Item.WALL
            "ROOT" -> grid[y][x] = Item.ROOT.also { registerOrgan() }
            "BASIC" -> grid[y][x] = Item.BASIC.also { registerOrgan() }
            "HARVESTER" -> grid[y][x] = Item.HARVESTER.also { registerOrgan() }
            "TENTACLE" -> grid[y][x] = Item.TENTACLE.also { registerOrgan() }
            "A" -> grid[y][x] = Item.A
        }
    }

    fun getMyOrgans(): Sequence<GridPoint> = allPoints.asSequence().filter { isOrgan(it) && isMy(it) }
    fun getEnemyRoot(): Sequence<GridPoint> = allPoints.asSequence().filter { isRoot(it) && isEnemy(it) }
    fun getProteinA(): Sequence<GridPoint> = allPoints.asSequence().filter { isA(it) }

    fun isA(point: GridPoint): Boolean = inbound(point) && grid[point.y][point.x] == Item.A
    fun isSpace(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPACE
    fun isOrgan(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT || grid[point.y][point.x] == Item.BASIC || grid[point.y][point.x] == Item.HARVESTER || grid[point.y][point.x] == Item.TENTACLE
    fun isRoot(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT
    fun isMy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ME
    fun isEnemy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ENEMY
    fun organId(point: GridPoint): Int = organIds[point.y][point.x]
    fun inbound(point: GridPoint) = point.x in 0 until grid[0].size && point.y in 0 until grid.size

    fun neighbours(point: GridPoint): List<GridPoint> {
        val result = mutableListOf<GridPoint>()
        for (dir in directions) {
            val pretender = point + dir
            if (inbound(pretender)) {
                result.add(GridPoint(pretender.x, pretender.y))
            }
        }
        return result
    }

    companion object {
        val NORTH = GridPoint(0, -1)
        val SOUTH = GridPoint(0, 1)
        val WEST = GridPoint(-1, 0)
        val EAST = GridPoint(1, 0)

        private val directions = listOf(
            NORTH, SOUTH, EAST, WEST
        )
    }

}

class Logic {

    lateinit var desk: Desk

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

            desk.neighbours(current).asSequence().filter { desk.isSpace(it) || desk.isA(it) }.forEach { neighbour ->
                queue.add(neighbour)
            }

        }
        return null
    }

    fun dist(a: GridPoint, b: GridPoint): Int = abs(a.x - b.x) + abs(a.y - b.y)

    fun minPath(from: GridPoint, to: Set<GridPoint>, filter: (GridPoint) -> Boolean): List<GridPoint>? {
        val queue = ArrayDeque<List<GridPoint>>()
        queue.add(listOf(from))
        val seen = mutableSetOf<GridPoint>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val lastElement = current.last()

            if (seen.contains(lastElement)) {
                continue
            }
            seen += lastElement

            if (to.contains(lastElement)) {
                return current
            }

            desk.neighbours(lastElement).asSequence().filter { filter(it)}.forEach { neighbour ->
                queue.add(current + neighbour)
            }

        }

        return null
    }

    fun minPath(from: Sequence<GridPoint>, to: Set<GridPoint>, filter: (GridPoint) -> Boolean): List<List<GridPoint>> {
        return from.map { minPath(it, to, filter) }.filterNotNull().filter { it.isNotEmpty() }.toList()
    }

    fun justGrow(): String {
        val pretenders = desk.getMyOrgans().asSequence().filter {
            desk.neighbours(it).any { desk.isSpace(it) }
        }.toList()

        if (pretenders.isEmpty()) {
            return "WAIT"
        }

        val organFrom = pretenders.random()
        val next = desk.neighbours(organFrom).asSequence().filter { desk.isSpace(it) }.toList().random()
        val organId = desk.organId(organFrom)
        val xTo = next.x
        val yTo = next.y
        return "GROW $organId $xTo $yTo BASIC"
    }

    fun goodForTentacle(): Sequence<GridPoint> = desk.allPoints.asSequence()
        .filter { desk.isSpace(it) }
        .filter { space ->
            val neighbours = desk.neighbours(space)
            val hasMyOrgan = neighbours.asSequence().any { desk.isOrgan(it) && desk.isMy(it) }
            val hasEnemyOrgan = neighbours.asSequence().any { desk.isOrgan(it) && desk.isEnemy(it) }
            hasMyOrgan && hasEnemyOrgan
        }

    fun dirCharByDirPoint(dirPos: GridPoint): Char = when(dirPos) {
        Desk.NORTH -> 'N'
        Desk.EAST -> 'E'
        Desk.SOUTH -> 'S'
        Desk.WEST -> 'W'
        else -> throw IllegalArgumentException("dirPos $dirPos is not a valid direction")
    }

    fun tentacleFromTo(myOrgan: GridPoint, enemyOrgan: GridPoint, destination: GridPoint): String {
        val dirPoint = enemyOrgan - destination
        val destChar = dirCharByDirPoint(dirPoint)
        val organId = desk.organId(myOrgan)
        val xTo = destination.x
        val yTo = destination.y
        return "GROW $organId $xTo $yTo TENTACLE $destChar"
    }

    fun basicFromTo(myOrgan: GridPoint, destination: GridPoint): String {
        val organId = desk.organId(myOrgan)
        val xTo = destination.x
        val yTo = destination.y
        return "GROW $organId $xTo $yTo BASIC"
    }

    fun pretenderToTentacle(pretender:GridPoint): String {
        check(desk.isSpace(pretender))

        val myOrgan = desk.neighbours(pretender).firstOrNull { desk.isOrgan(it) && desk.isMy(it) }
        checkNotNull(myOrgan)
        val enemyOrgan = desk.neighbours(pretender).firstOrNull { desk.isOrgan(it) && desk.isEnemy(it) }
        checkNotNull(enemyOrgan)

        return tentacleFromTo(myOrgan, enemyOrgan, pretender)
    }

    fun pretenderToBasic(pretender:GridPoint): String {
        check(desk.isSpace(pretender))

        val myOrgan = desk.neighbours(pretender).firstOrNull { desk.isOrgan(it) && desk.isMy(it) }
        checkNotNull(myOrgan)

        return basicFromTo(myOrgan, pretender)
    }

    fun justGrowTo(target: GridPoint) : String {
        val spacePretender = desk.allPoints.asSequence()
            .filter { desk.isSpace(it) }
            .filter { space ->
                val neighbours = desk.neighbours(space)
                val hasMyOrgan = neighbours.asSequence().any { desk.isOrgan(it) && desk.isMy(it) }
                hasMyOrgan
            }.minByOrNull { dist(it, target) }
        return if (spacePretender == null) {
            "WAIT"
        } else {
            pretenderToBasic(spacePretender)
        }
    }

    fun moveWood2League() : String {
        val primaryTarget = desk.getEnemyRoot().firstOrNull()
        if (primaryTarget == null) {
            return justGrow()
        }

        val tenPretenders = goodForTentacle().toList()
        if (tenPretenders.isNotEmpty()) {
            val tentacle = tenPretenders.asSequence().minByOrNull{
                dist(it, primaryTarget)
            }
            return pretenderToTentacle(tentacle!!)
        }

        return justGrowTo(primaryTarget)
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

            val move = logic.moveWood2League()
            println(move)
        }
    }
}


