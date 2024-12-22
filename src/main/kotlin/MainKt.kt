import java.util.*
import kotlin.math.abs

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

fun debug(v: String) = System.err.println(v)

data class ProteinStock(val a: Int, val b: Int, val c: Int, val d: Int)

class Desk(val width: Int, val height: Int, val allPoints: List<GridPoint>) {

    private enum class Item {
        SPACE, WALL, ROOT, BASIC, A, HARVESTER, TENTACLE, SPORER
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
        }
    }

    fun getMyOrgans(organRootId: Int): Sequence<GridPoint> = allPoints.asSequence().filter { isOrgan(it) && isMy(it) && organRootId(it) == organRootId }
    fun getMyRoots(): Sequence<GridPoint> = allPoints.asSequence().filter { isRoot(it) && isMy(it) }
    fun getEnemyRoots(): Sequence<GridPoint> = allPoints.asSequence().filter { isRoot(it) && isEnemy(it) }
    fun getProteinA(): Sequence<GridPoint> = allPoints.asSequence().filter { isA(it) }

    fun isA(point: GridPoint): Boolean = inbound(point) && grid[point.y][point.x] == Item.A
    fun isSpace(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPACE
    fun isOrgan(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT || grid[point.y][point.x] == Item.BASIC || grid[point.y][point.x] == Item.HARVESTER || grid[point.y][point.x] == Item.TENTACLE || grid[point.y][point.x] == Item.SPORER
    fun isRoot(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT
    fun isSporer(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPORER
    fun isHarvester(point: GridPoint): Boolean = grid[point.y][point.x] == Item.HARVESTER
    fun isMy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ME
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
}

class Logic {
    lateinit var desk: Desk

    fun dirCharByDirPoint(dirPos: GridPoint): Char = when(dirPos) {
        Desk.NORTH -> 'N'
        Desk.EAST -> 'E'
        Desk.SOUTH -> 'S'
        Desk.WEST -> 'W'
        else -> throw IllegalArgumentException("dirPos $dirPos is not a valid direction")
    }

    fun move(orgNum: Int): String {
        val currentRoot = desk.getMyRoots().sortedBy { desk.organId(it) }.drop(orgNum).first()
        val currentRootOrganId = desk.organRootId(currentRoot)
        debug("root: $currentRootOrganId")
        return Move.WAIT
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
        logic.desk = desk
        for (i in 0 until requiredActionsCount) {

            // Write an action using println()
            // To debug: System.err.println("Debug messages...");

            val move = logic.move(i)
            println(move)
        }
    }
}


