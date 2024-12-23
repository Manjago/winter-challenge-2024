import java.util.*
import kotlin.math.abs

lateinit var desk: Desk

data class GridPoint(val x: Int, val y: Int) {
    operator fun plus(other: GridPoint): GridPoint = GridPoint(x + other.x, y + other.y)
    operator fun minus(other: GridPoint): GridPoint = GridPoint(x - other.x, y - other.y)
    fun isSameLine(other: GridPoint): Boolean = this.x == other.x || y == other.y
    override fun toString(): String = "($x,$y)"
}

fun dist(a: GridPoint, b: GridPoint): Int = abs(a.x - b.x) + abs(a.y - b.y)

fun sgn(a: Int) : Int = when {
    a < 0 -> -1
    a > 0 -> +1
    else -> 0
}

fun normalizeDirPoint(rawDirPoint: GridPoint): GridPoint {
    return GridPoint(sgn(rawDirPoint.x), sgn(rawDirPoint.y))
}

fun log(v: String) = System.err.println(v)

data class ProteinStock(val a: Int, val b: Int, val c: Int, val d: Int) {
    fun enoughFor(other: ProteinStock): Boolean {
       val diff = this - other
       return diff.notNegative()
    }
    fun notNegative(): Boolean = a>=0 && b>=0 && c>=0 && d >= 0
    operator fun minus(other: ProteinStock): ProteinStock {
        return ProteinStock(a - other.a, b - other.b, c - other.c, d - other.d)
    }
    companion object {
        val ROOT = ProteinStock(1, 1, 1, 1)
        val BASIC = ProteinStock(1, 0, 0, 0)
        val HARVESTER = ProteinStock(0, 0, 1, 1)
        val TENTACLE = ProteinStock(0, 1, 1, 0)
        val SPORER = ProteinStock(0, 1, 0, 1)
    }
}

enum class Item {
    SPACE, WALL, ROOT, BASIC, A, B, C, D, HARVESTER, TENTACLE, SPORER
}

class Desk(val width: Int, val height: Int, val allPoints: List<GridPoint>) {

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

    private enum class OrganDir {
        N, W, E, S, X;
        companion object {
            fun parseString(v: String) : OrganDir = when (v) {
                "N" -> N
                "W" -> W
                "E" -> E
                "S" -> S
                "X" -> X
                else -> throw IllegalStateException("Invalid Organ dir: $v")
            }
        }
    }

    private val grid: Array<Array<Item>> = Array(height) { Array(width) { Item.SPACE } }
    private val meOrEnemy: Array<Array<MeOrEnemy>> =
        Array(height) { Array(width) { MeOrEnemy.UNKNOWN } }
    private val organIds: Array<Array<Int>> = Array(height) { Array(width) { -1 } }
    private val organRootIds: Array<Array<Int>> = Array(height) { Array(width) { -1 } }
    private val organParentIds: Array<Array<Int>> = Array(height) { Array(width) { -1 } }
    private val organDirections: Array<Array<OrganDir>> = Array(height) { Array(width) { OrganDir.X } }

    lateinit var myStock: ProteinStock
    lateinit var enemyStock: ProteinStock

    fun fill(x: Int, y: Int, type: String, owner: Int, organId: Int, organRootId: Int, organParentId: Int, organDir: String) {

        fun registerOrgan() {
            meOrEnemy[y][x] = MeOrEnemy.byInt(owner)
            organIds[y][x] = organId
            organRootIds[y][x] = organRootId
            organParentIds[y][x] = organParentId
            organDirections[y][x] = OrganDir.parseString(organDir)
        }

        when (type) {
            "WALL" -> grid[y][x] = Item.WALL
            "ROOT" -> grid[y][x] = Item.ROOT.also { registerOrgan() }
            "BASIC" -> grid[y][x] = Item.BASIC.also { registerOrgan() }
            "HARVESTER" -> grid[y][x] = Item.HARVESTER.also { registerOrgan() }
            "TENTACLE" -> grid[y][x] = Item.TENTACLE.also { registerOrgan() }
            "SPORER" -> grid[y][x] = Item.SPORER.also { registerOrgan() }
            "A" -> grid[y][x] = Item.A
            "B" -> grid[y][x] = Item.B
            "C" -> grid[y][x] = Item.C
            "D" -> grid[y][x] = Item.D
            else -> throw IllegalStateException("Invalid type: $type")
        }
    }

    fun getMyOrgans(organRootId: Int): Sequence<GridPoint> = allPoints.asSequence().filter { isOrgan(it) && isReallyMy(it, organRootId)}
    fun getMyRoots(): Sequence<GridPoint> = allPoints.asSequence().filter { isRoot(it) && isMy(it) }
    fun getEnemyRoots(): Sequence<GridPoint> = allPoints.asSequence().filter { isRoot(it) && isEnemy(it) }
    fun getProteinA(): Sequence<GridPoint> = allPoints.asSequence().filter { isA(it) }

    fun isA(point: GridPoint): Boolean = inbound(point) && grid[point.y][point.x] == Item.A
    fun isSpace(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPACE
    fun isOrgan(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT || grid[point.y][point.x] == Item.BASIC || grid[point.y][point.x] == Item.HARVESTER || grid[point.y][point.x] == Item.TENTACLE || grid[point.y][point.x] == Item.SPORER
    fun isRoot(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT
    fun isSporer(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPORER
    fun isHarvester(point: GridPoint): Boolean = grid[point.y][point.x] == Item.HARVESTER
    private fun isMy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ME
    fun isReallyMy(point: GridPoint, organRootId: Int): Boolean = isMy(point) && organRootId == organRootId(point)
    fun isEnemy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ENEMY
    fun organId(point: GridPoint): Int = organIds[point.y][point.x]
    fun organRootId(point: GridPoint): Int = organRootIds[point.y][point.x]
    fun inbound(point: GridPoint) = point.x in 0 until width && point.y in 0 until height

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

object Move {
    val WAIT = "WAIT"
    fun growBasic(organFrom: GridPoint, growTo: GridPoint): String {
        val organId = desk.organId(organFrom)
        val xTo = growTo.x
        val yTo = growTo.y
        return "GROW $organId $xTo $yTo BASIC"
    }

    fun growHarvester(organFrom: GridPoint, growTo: GridPoint, forSource: GridPoint): String {
        val dir = normalizeDirPoint(forSource - growTo)
        val dirChar = dirCharByDirPoint(dir)
        val organId = desk.organId(organFrom)
        val xTo = growTo.x
        val yTo = growTo.y
        return "GROW $organId $xTo $yTo HARVESTER $dirChar"
    }

    private fun dirCharByDirPoint(dirPos: GridPoint): Char = when(dirPos) {
        Desk.NORTH -> 'N'
        Desk.EAST -> 'E'
        Desk.SOUTH -> 'S'
        Desk.WEST -> 'W'
        else -> throw IllegalArgumentException("dirPos $dirPos is not a valid direction")
    }
}

object Path {
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

    fun minPathSeq(from: Sequence<GridPoint>, to: Set<GridPoint>, filter: (GridPoint) -> Boolean): List<List<GridPoint>> {
        return from.map { minPath(it, to, filter) }.filterNotNull().filter { it.isNotEmpty() }.toList()
    }
}

class Sensor {
    fun isNeedTentacles(): Boolean {
        //todo
        return false
    }
    fun isMaySpore(): Boolean {
        //todo
        return false
    }
    fun isNeedResources(): Boolean {
        //todo
        return false
    }
    fun isNeedProteinASource(currentRootOrganId: Int): Boolean {

        val hasActiveHarvA = desk.allPoints.asSequence()
            .filter { desk.isHarvester(it) && desk.isReallyMy(it, currentRootOrganId) }
            .filter { desk.neighbours(it).any { desk.isA(it) } }
            .any()

        if (hasActiveHarvA) {
            return false
        }

        if (!desk.myStock.enoughFor( ProteinStock.HARVESTER )) {
            log("no harv!")
            return false
        }

        return true
    }
}

class Action {
    fun doHarvForA(currentRootOrganId: Int): String {
        //todo
        val allA = desk.allPoints.asSequence()
            .filter { desk.isA(it) }.toSet()

        val myOrgans = desk.getMyOrgans(currentRootOrganId)

        val paths = Path.minPathSeq(myOrgans, allA, desk::isSpace).filter { it.size <= 3 }
        if (paths.isEmpty()) {
            log("not 'a' path")
            return Move.WAIT
        }

        val minPath = paths.minBy { it.size }
        // organ -  ...   - pretender - a
        val fromOrgan = minPath.first()
        val beforeLastIndex = minPath.size - 2
        val pretender = minPath[beforeLastIndex]

        return Move.growHarvester(fromOrgan, pretender, minPath.last())
    }
    fun doTentacles(): String {
        //todo
        return Move.WAIT
    }
    fun doSpore(): String {
        //todo
        return Move.WAIT
    }
    fun obtainResources(): String {
        //todo
        return Move.WAIT
    }

    fun justGrow(currentOrganRootId: Int): String {
        val pretenders = desk.getMyOrgans(currentOrganRootId).asSequence().filter {
            desk.neighbours(it).any { desk.isSpace(it) }
        }.toList()

        if (pretenders.isEmpty()) {
            return "WAIT"
        }

        val organFrom = pretenders.random()
        val next = desk.neighbours(organFrom).asSequence().filter { desk.isSpace(it) }.toList().random()
        return Move.growBasic(organFrom, next).also { log("justGrow") }
    }
}

class Logic {

    val sensor = Sensor()
    val action = Action()

    fun move(orgNum: Int): String {
        val currentRoot = desk.getMyRoots().sortedBy { desk.organId(it) }.drop(orgNum).first()
        val currentRootOrganId = desk.organRootId(currentRoot)
        log("root: $currentRootOrganId")

        return when {
            sensor.isNeedProteinASource(currentRootOrganId) -> action.doHarvForA(currentRootOrganId)
            sensor.isNeedTentacles() -> action.doTentacles()
            sensor.isMaySpore() -> action.doSpore()
            sensor.isNeedResources() -> action.obtainResources()
            else -> action.justGrow(currentRootOrganId)
        }

    }

}

fun main() {
    val start = System.currentTimeMillis()
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
    val initialLoad = System.currentTimeMillis()
    log("Initial ${initialLoad - start} ms")

    // game loop
    while (true) {
        val loopStart = System.currentTimeMillis()
        desk = Desk(width, height, allPoints)
        val entityCount = input.nextInt()
        repeat(entityCount) {
            val x = input.nextInt()
            val y = input.nextInt() // grid coordinate
            val type = input.next() // WALL, ROOT, BASIC, TENTACLE, HARVESTER, SPORER, A, B, C, D
            val owner = input.nextInt() // 1 if your organ, 0 if enemy organ, -1 if neither
            val organId = input.nextInt() // id of this entity if it's an organ, 0 otherwise
            val organDir = input.next() // N,E,S,W or X if not an organ
            val organParentId = input.nextInt()
            val organRootId = input.nextInt()

            desk.fill(x, y, type, owner, organId, organRootId, organParentId, organDir)
        }

        val myA = input.nextInt()
        val myB = input.nextInt()
        val myC = input.nextInt()
        val myD = input.nextInt() // your protein stock
        desk.myStock = ProteinStock(myA, myB, myC, myD)
        val oppA = input.nextInt()
        val oppB = input.nextInt()
        val oppC = input.nextInt()
        val oppD = input.nextInt() // opponent's protein stock
        desk.enemyStock = ProteinStock(oppA, oppB, oppC, oppD)
        val requiredActionsCount =
            input.nextInt() // your number of organisms, output an action for each one in any order
        //System.err.println(display())
        for (i in 0 until requiredActionsCount) {

            // Write an action using println()
            // To debug: System.err.println("Debug messages...");

            val move = logic.move(i)
            println(move)
        }
        val loopStop = System.currentTimeMillis()
        log("In loop ${loopStop - loopStart} ms")
    }
}


