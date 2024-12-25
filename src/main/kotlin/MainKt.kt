import java.io.BufferedReader
import java.io.InputStreamReader
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

fun sgn(a: Int): Int = when {
    a < 0 -> -1
    a > 0 -> +1
    else -> 0
}

fun normalizeDirPoint(rawDirPoint: GridPoint): GridPoint {
    return GridPoint(sgn(rawDirPoint.x), sgn(rawDirPoint.y))
}

fun dirCharByDirPoint(dirPos: GridPoint): Char = when (dirPos) {
    Desk.NORTH -> 'N'
    Desk.EAST -> 'E'
    Desk.SOUTH -> 'S'
    Desk.WEST -> 'W'
    else -> throw IllegalArgumentException("dirPos $dirPos is not a valid direction")
}

val logSB = StringBuilder()
fun log(v: String) = logSB.append(v).append("\n")
fun logFlush() {
    System.err.println(logSB)
    logSB.clear()
}

data class ProteinStock(val a: Int, val b: Int, val c: Int, val d: Int) {
    fun enoughFor(other: ProteinStock): Boolean {
        val diff = this - other
        return diff.notNegative()
    }

    fun notNegative(): Boolean = a >= 0 && b >= 0 && c >= 0 && d >= 0
    operator fun minus(other: ProteinStock): ProteinStock =
        ProteinStock(a - other.a, b - other.b, c - other.c, d - other.d)

    operator fun plus(other: ProteinStock): ProteinStock =
        ProteinStock(a + other.a, b + other.b, c + other.c, d + other.d)

    companion object {
        val ROOT = ProteinStock(1, 1, 1, 1)
        val BASIC = ProteinStock(1, 0, 0, 0)
        val HARVESTER = ProteinStock(0, 0, 1, 1)
        val TENTACLE = ProteinStock(0, 1, 1, 0)
        val SPORER = ProteinStock(0, 1, 0, 1)
        val SPORE_LIMIT = ProteinStock(2, 2, 2, 2)
    }
}

enum class Item {
    SPACE, WALL, ROOT, BASIC, A, B, C, D, HARVESTER, TENTACLE, SPORER
}

class Desk(val width: Int, val height: Int, val allPoints: List<GridPoint>) {

    private val center = GridPoint(width / 2, height / 2)

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

    private enum class OrganDir(val dirPoint: GridPoint) {
        N(NORTH), W(WEST), E(EAST), S(SOUTH), X(NORTH);

        companion object {
            fun parseString(v: String): OrganDir = when (v) {
                "N" -> N
                "W" -> W
                "E" -> E
                "S" -> S
                "X" -> X
                else -> throw IllegalStateException("Invalid Organ dir: $v")
            }
        }
    }

    fun distToCenter(point: GridPoint): Int = dist(center, point)

    private val grid: Array<Array<Item>> = Array(height) { Array(width) { Item.SPACE } }
    private val meOrEnemy: Array<Array<MeOrEnemy>> = Array(height) { Array(width) { MeOrEnemy.UNKNOWN } }
    private val organIds: Array<Array<Int>> = Array(height) { Array(width) { -1 } }
    private val organRootIds: Array<Array<Int>> = Array(height) { Array(width) { -1 } }
    private val organParentIds: Array<Array<Int>> = Array(height) { Array(width) { -1 } }
    private val organDirs: Array<Array<OrganDir>> = Array(height) { Array(width) { OrganDir.X } }

    lateinit var myStock: ProteinStock
    lateinit var enemyStock: ProteinStock

    fun fill(
        x: Int, y: Int, type: String, owner: Int, organId: Int, organRootId: Int, organParentId: Int, organDir: String
    ) {

        fun registerOrgan() {
            meOrEnemy[y][x] = MeOrEnemy.byInt(owner)
            organIds[y][x] = organId
            organRootIds[y][x] = organRootId
            organParentIds[y][x] = organParentId
            organDirs[y][x] = OrganDir.parseString(organDir)
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

    fun getMyOrgans(organRootId: Int): Sequence<GridPoint> =
        allPoints.asSequence().filter { isOrgan(it) && isReallyMy(it, organRootId) }
    fun getEnemyOrgans(): Sequence<GridPoint> =
        allPoints.asSequence().filter { isOrgan(it) && isEnemy(it) }

    fun getMyRoots(): Sequence<GridPoint> = allPoints.asSequence().filter { isRoot(it) && isMy(it) }
    fun getEnemyRoots(): Sequence<GridPoint> = allPoints.asSequence().filter { isRoot(it) && isEnemy(it) }
    fun getProteinA(): Sequence<GridPoint> = allPoints.asSequence().filter { isA(it) }

    fun isA(point: GridPoint): Boolean = inbound(point) && grid[point.y][point.x] == Item.A
    fun isB(point: GridPoint): Boolean = inbound(point) && grid[point.y][point.x] == Item.B
    fun isC(point: GridPoint): Boolean = inbound(point) && grid[point.y][point.x] == Item.C
    fun isD(point: GridPoint): Boolean = inbound(point) && grid[point.y][point.x] == Item.D
    fun isSpace(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPACE
    fun isSpaceOrProteinNotA(point: GridPoint): Boolean =
        desk.isSpace(point) || desk.isB(point) || desk.isC(point) || desk.isD(point)

    fun isSpaceOrProtein(point: GridPoint): Boolean =
        desk.isSpace(point) || desk.isA(point) || desk.isB(point) || desk.isC(point) || desk.isD(point)

    fun isProtein(point: GridPoint): Boolean = desk.isA(point) || desk.isB(point) || desk.isC(point) || desk.isD(point)
    fun isEnemyTentacle(point: GridPoint): Boolean = desk.inbound(point) && desk.isEnemy(point) && desk.isTentacle(point)
    fun isOrgan(point: GridPoint): Boolean =
        grid[point.y][point.x] == Item.ROOT || grid[point.y][point.x] == Item.BASIC || grid[point.y][point.x] == Item.HARVESTER || grid[point.y][point.x] == Item.TENTACLE || grid[point.y][point.x] == Item.SPORER

    fun isRoot(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT
    fun isSporer(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPORER
    fun isHarvester(point: GridPoint): Boolean = grid[point.y][point.x] == Item.HARVESTER
    fun isTentacle(point: GridPoint): Boolean = grid[point.y][point.x] == Item.TENTACLE
    fun isMy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ME
    fun isReallyMy(point: GridPoint, organRootId: Int): Boolean = isMy(point) && organRootId == organRootId(point)
    fun isEnemy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ENEMY
    fun organId(point: GridPoint): Int = organIds[point.y][point.x]
    fun organDir(point: GridPoint): GridPoint = organDirs[point.y][point.x].dirPoint
    fun organRootId(point: GridPoint): Int = organRootIds[point.y][point.x]
    fun organParentId(point: GridPoint): Int = organParentIds[point.y][point.x]
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

    fun neighbours2(point: GridPoint): List<GridPoint> {
        val result = mutableSetOf<GridPoint>()
        for (dir in directions) {
            val pretender = point + dir
            if (inbound(pretender)) {
                val element = GridPoint(pretender.x, pretender.y)
                result.add(element)
                result.addAll(neighbours(element))
            }
        }
        return result.toList()
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

sealed interface Move {

    fun toProtocolMove() : String

    enum class Wait : Move {
        INSTANCE {
            override fun toProtocolMove(): String = "WAIT"
        };
    }

    class Basic(val organFrom: GridPoint, val growTo: GridPoint) : Move {
        override fun toProtocolMove(): String {
            desk.myStock -= ProteinStock.BASIC
            val organId = desk.organId(organFrom)
            val xTo = growTo.x
            val yTo = growTo.y
            return "GROW $organId $xTo $yTo BASIC"
        }
    }

    class Harvester(val organFrom: GridPoint, val growTo: GridPoint, val forSource: GridPoint) : Move {
        override fun toProtocolMove(): String {
            desk.myStock -= ProteinStock.HARVESTER
            val dir = normalizeDirPoint(forSource - growTo)
            val dirChar = dirCharByDirPoint(dir)
            val organId = desk.organId(organFrom)
            val xTo = growTo.x
            val yTo = growTo.y
            return "GROW $organId $xTo $yTo HARVESTER $dirChar"
        }
    }
    class Sporer(val organFrom: GridPoint, val growTo: GridPoint, val forSource: GridPoint) : Move {
        override fun toProtocolMove(): String {
            desk.myStock -= ProteinStock.SPORER
            val dir = normalizeDirPoint(forSource - growTo)
            val dirChar = dirCharByDirPoint(dir)
            val organId = desk.organId(organFrom)
            val xTo = growTo.x
            val yTo = growTo.y
            return "GROW $organId $xTo $yTo SPORER $dirChar"
        }
    }
    class Spore(val organFrom: GridPoint, val growTo: GridPoint) : Move {
        override fun toProtocolMove(): String {
            desk.myStock -= ProteinStock.ROOT
            val organId = desk.organId(organFrom)
            val xTo = growTo.x
            val yTo = growTo.y
            return "SPORE $organId $xTo $yTo"
        }
    }

    class Tentacle(val organFrom: GridPoint, val growTo: GridPoint, val forVictim: GridPoint) : Move {
        override fun toProtocolMove(): String {
            val dir = normalizeDirPoint(forVictim - growTo)
            val dirChar = dirCharByDirPoint(dir)
            val organId = desk.organId(organFrom)
            val xTo = growTo.x
            val yTo = growTo.y
            return "GROW $organId $xTo $yTo TENTACLE $dirChar"
        }
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

            desk.neighbours(lastElement).asSequence().filter { filter(it) }.forEach { neighbour ->
                queue.add(current + neighbour)
            }

        }

        return null
    }

    fun minPathSeq(
        from: Sequence<GridPoint>, to: Set<GridPoint>, filter: (GridPoint) -> Boolean
    ): List<List<GridPoint>> {
        return from.map { minPath(it, to, filter) }.filterNotNull().filter { it.isNotEmpty() }.toList()
    }
}

class Logic {

    val harvProcess = mutableMapOf<Char, Boolean>()

    enum class SporeState {
        NONE, SPORER, SPORE
    }

    val sporeStat = mutableMapOf<Int, SporeState>()

    fun line(from: GridPoint, dir: GridPoint, noToSporer: Boolean = false) : List<GridPoint> {
        val result = mutableListOf<GridPoint>()

        result.add(from)
        var pretender = from

        pretender += dir
        while(desk.inbound(pretender) && desk.isSpaceOrProtein(pretender)) {
            result.add(pretender)
            pretender += dir
        }

        if (desk.inbound(pretender) && desk.isSporer(pretender)) {
            log("no shoot to sporer")
            result.clear()
            result += pretender
        }

        return result
    }

    fun doSpore(currentRootOrganId: Int): Move? {
        val sporeState = sporeStat.getOrPut(currentRootOrganId) { SporeState.NONE }

        if (!desk.myStock.enoughFor(ProteinStock.SPORE_LIMIT)) {
            log("spore limit")
        }

        when(sporeState) {
            SporeState.NONE -> {
                if (!desk.myStock.enoughFor(ProteinStock.SPORER + ProteinStock.ROOT)) {
                    log("no res for sporer and spore")
                    return null
                }
               val pretenders = desk.getMyOrgans(currentRootOrganId).asSequence()
                   .flatMap { desk.neighbours(it).asSequence().filter { desk.isSpaceOrProtein(it) } }
                   .toList()
               if (pretenders.isEmpty()) {
                   log("no room for sporer")
                   return null
               }

               val pretender = pretenders.asSequence().flatMap {
                    listOf(
                    line(it, Desk.NORTH),
                    line(it, Desk.EAST),
                    line(it, Desk.WEST),
                    line(it, Desk.SOUTH))
                }.filter { it.size > 1 }
                   .maxByOrNull { it.size }

                if (pretender == null) {
                    log("no good room for sporer")
                    return null
                }

                val organ = desk.neighbours(pretender.first()).asSequence().first {
                    desk.isReallyMy(it, currentRootOrganId) && desk.isOrgan(it) }


                log("set sporer from $organ to ${pretender.first()} for ${pretender.last()}")
                sporeStat[currentRootOrganId] = SporeState.SPORER
                return Move.Sporer(organ, pretender.first(), pretender.last())
            }
            SporeState.SPORER -> {

                if (!desk.myStock.enoughFor(ProteinStock.ROOT)) {
                    log("no res for SPORE")
                    return null
                }

                val sporer = desk.getMyOrgans(currentRootOrganId).asSequence().firstOrNull {
                    desk.isSporer(it)
                }
                if (sporer == null) {
                    sporeStat[currentRootOrganId] = SporeState.NONE
                    log("sporer lost")
                    return doSpore(currentRootOrganId)
                }

                val line = line(sporer, desk.organDir(sporer))
                if (line.size > 1) {
                    log("spore fro $sporer to ${line.last()}")
                    sporeStat[currentRootOrganId] = SporeState.SPORE
                    return Move.Spore(sporer, line.last())
                } else {
                    log("too short for spore")
                    return null
                }
            }
            SporeState.SPORE -> {
                log("already make spore")
                return null
            }
        }

        return null
    }

    fun doHarvFor(currentRootOrganId: Int, sourceChar: Char, sourceFun: (GridPoint) -> Boolean): Move? {

        if (harvProcess[sourceChar]!!) {
            log("work on $sourceChar")
            return null
        }

        if (!isNeedProteinSource(sourceChar, sourceFun)) {
            log("no need $sourceChar")
            return null
        }

        val allAPretenders = desk.allPoints.asSequence().filter { sourceFun(it) }
            .flatMap { desk.neighbours(it).filter { desk.isSpace(it) }.asSequence() }.toSet()

        val myOrgans = desk.getMyOrgans(currentRootOrganId)

        val paths = Path.minPathSeq(myOrgans, allAPretenders)
        { desk.isSpace(it) || (desk.isProtein(it) && !sourceFun(it)) }
        if (paths.isEmpty()) {
            log("not '$sourceChar' path")
            return null
        }

        val minPath = paths.minBy { it.size }
        log("found '$sourceChar' path $minPath")

        harvProcess[sourceChar] = true

        // organ -  ...   - pretender
        // or
        // organ -  pretender

        if (minPath.size == 2) {
            val fromOrgan = minPath.first()
            val pretender = minPath.last()
            val aSource = desk.neighbours(pretender).asSequence().filter { sourceFun(it) }.first()
            log("first $sourceChar harv")
            return Move.Harvester(fromOrgan, pretender, aSource)
        } else {
            log("goto $sourceChar harv")
            return Move.Basic(minPath.first(), minPath[1])
        }
    }

    fun bfsTo(from: GridPoint, target: (GridPoint) -> Boolean) : List<List<GridPoint>> {
        val result = mutableListOf<List<GridPoint>>()
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

            result += current

            if (target(lastElement)) {
                continue
            }

            desk.neighbours(lastElement).asSequence().filter { desk.isSpaceOrProtein(it) }.forEach { neighbour ->
                queue.add(current + neighbour)
            }
        }

        return result
    }

    fun bfs(from: GridPoint, maxSize: Int) : List<List<GridPoint>> {
        val result = mutableListOf<List<GridPoint>>()
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

            if (current.size > maxSize) {
                continue
            }

            result += current

            desk.neighbours(lastElement).asSequence().filter { desk.isSpaceOrProtein(it) }.forEach { neighbour ->
                queue.add(current + neighbour)
            }
        }

        return result
    }

    fun doTentacles(currentRootOrganId: Int): Move? {

        if (!desk.myStock.enoughFor(ProteinStock.TENTACLE)) {
            log("no energy for t")
            return null
        }

        val alarm = desk.getMyOrgans(currentRootOrganId).asSequence()
            .flatMap {
                desk.neighbours(it).asSequence().filter { desk.isSpaceOrProtein(it) }
                    .filter { desk.neighbours2(it).asSequence().any { desk.isEnemyTentacle(it) } }
            }.toList()


        if (alarm.isNotEmpty()) {
            val enemyTentaclePath = bfs(alarm.first(), 2).asSequence()
                .firstOrNull { it.size == 2 && desk.isEnemyTentacle(it.last()) }
            if (enemyTentaclePath == null) {
                log("alarm but i fail")
            } else {
                val organ = desk.neighbours(enemyTentaclePath.first()).asSequence().first {
                    desk.isReallyMy(it, currentRootOrganId)
                }
                log("alarm ten from $organ grow ${enemyTentaclePath.first()} for ${enemyTentaclePath[1]}")
                return Move.Tentacle(organ, enemyTentaclePath.first(), enemyTentaclePath[1])
            }


        }


        val placeForTentacles: List<GridPoint> = desk.getEnemyOrgans().asSequence()
            // near enemy
            .flatMap {
                desk.neighbours(it).asSequence().filter { desk.isSpaceOrProtein(it) }
                    .filter { desk.neighbours(it).asSequence().any { desk.isReallyMy(it, currentRootOrganId) } }
            }.toList()

        if (placeForTentacles.isEmpty()) {
            log("no room for t")
            return null
        }

        data class TentacleVictim(val ten: GridPoint, val victim: GridPoint, val organFrom: GridPoint)

        val m = placeForTentacles.asSequence().map {
            val n = desk.neighbours(it)
            val victim = n.first { desk.isEnemy(it) }
            val myOrgan = n.first { desk.isOrgan(it) && desk.isReallyMy(it, currentRootOrganId) }
            TentacleVictim(it, victim, myOrgan)
        }.minBy { desk.organParentId(it.victim) }

        log("try ten from ${m.organFrom} to ${m.ten} for ${m.victim} with pid ${desk.organParentId(m.victim)}")
        return Move.Tentacle(m.organFrom, m.ten, m.victim)
    }

    fun List<GridPoint>.selectByDistToCenter(): GridPoint {
        check(this.isNotEmpty())
        return this.asSequence().minBy { desk.distToCenter(it) }
    }

    fun justGrow(currentOrganRootId: Int): Move? {
        val pretenders = desk.getMyOrgans(currentOrganRootId).asSequence().filter {
            desk.neighbours(it).any { desk.isSpaceOrProteinNotA(it) }
        }.toList()

        if (pretenders.isEmpty()) {
            log("grow fail")
            return null
        }

        val route = pretenders.asSequence()
            .flatMap { bfsTo(it, desk::isProtein) }
            .filter { it.size > 1}
            .filter {desk.isSpace(it[1]) }
            .filter { desk.isProtein(it.last()) }
            .firstOrNull()

        val (organFrom, next) = if (route != null) {
            log("route fr ${route.first()} to ${route[1]} cz ${route.last()}")
            route.first() to route[1]
        } else {
            val organFrom = pretenders.random()
            val next = desk.neighbours(organFrom).asSequence().filter { desk.isSpaceOrProteinNotA(it) }.toList().random()
            organFrom to next
        }

        val mayBeProtein = desk.neighbours(next).asSequence().filter { desk.isProtein(it) }.firstOrNull()
        val canGrowHarvester = desk.myStock.enoughFor(ProteinStock.HARVESTER)
        val needGrowHarvester = if (mayBeProtein != null) {
            desk.neighbours(mayBeProtein).asSequence().any { desk.isHarvester(it) }.not()
        } else {
            false
        }
        return if (mayBeProtein != null && canGrowHarvester && needGrowHarvester) {
            log("idle harv")
            Move.Harvester(organFrom, next, mayBeProtein).also { log("just harv") }
        } else {
            log("idle basic")
            Move.Basic(organFrom, next).also { log("justGrow") }
        }

    }

    fun agressiveGrow(currentOrganRootId: Int): Move? {

        val pretenders = desk.getMyOrgans(currentOrganRootId).asSequence().filter {
            desk.neighbours(it).any { desk.isSpaceOrProtein(it) }
        }.toList()

        if (pretenders.isEmpty()) {
            log("agressive fail")
            return null
        }

        val organFrom = pretenders.selectByDistToCenter()
        val next = desk.neighbours(organFrom).asSequence().filter { desk.isSpaceOrProtein(it) }.toList().random()
        return Move.Basic(organFrom, next).also { log("aggrGrow") }
    }

    fun isNeedProteinSource(sourceChar: Char, sourceFun: (GridPoint) -> Boolean): Boolean {

        //@formatter:off
        val hasActiveHarv =
            desk.allPoints.asSequence()
                .filter { desk.isHarvester(it) && desk.isMy(it) }
                .filter { desk.neighbours(it).any { sourceFun(it) } }.any()
        //@formatter:on

        if (hasActiveHarv) {
            log("has harv $sourceChar")
            return false
        }

        if (!desk.myStock.enoughFor(ProteinStock.HARVESTER)) {
            log("no harv res")
            return false
        }

        log("need harv $sourceChar")
        return true
    }


    fun initBeforeMoves() {
        harvProcess[A_CHAR] = false
        harvProcess[B_CHAR] = false
        harvProcess[C_CHAR] = false
        harvProcess[D_CHAR] = false
    }

    fun move(orgNum: Int): String {

        val currentRoot = desk.getMyRoots().sortedBy { desk.organId(it) }.drop(orgNum).first()
        val currentRootOrganId = desk.organRootId(currentRoot)
        log("root: $currentRootOrganId")

        //@formatter:off
        val result =
            doSpore(currentRootOrganId) ?:
            doHarvFor(currentRootOrganId, A_CHAR, desk::isA) ?:
            doTentacles(currentRootOrganId) ?:
            doHarvFor(currentRootOrganId, B_CHAR, desk::isB) ?:
            doHarvFor(currentRootOrganId, C_CHAR, desk::isC) ?:
            doHarvFor(currentRootOrganId, D_CHAR, desk::isD) ?:
            justGrow(currentRootOrganId) ?:
            agressiveGrow(currentRootOrganId) ?:
            Move.Wait.INSTANCE
        //@formatter:on

        return result.toProtocolMove()
    }

    companion object {
        const val A_CHAR = 'A'
        const val B_CHAR = 'B'
        const val C_CHAR = 'C'
        const val D_CHAR = 'D'
    }

}

fun mainLoop() {

    fun StringTokenizer.nextInt(): Int = this.nextToken().toInt()

    fun BufferedReader.nextLine(): StringTokenizer = StringTokenizer(this.readLine())

    BufferedReader(InputStreamReader(System.`in`)).use { br ->

        val start = System.currentTimeMillis()
        val logic = Logic()
        val (width, height) = with(br.nextLine()) {
            val width = nextInt() // columns in the game grid
            val height = nextInt() // rows in the game grid
            width to height
        }

        val allPoints = mutableListOf<GridPoint>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                allPoints.add(GridPoint(x, y))
            }
        }
        val initialLoad = System.currentTimeMillis()
        log("Initial ${initialLoad - start} ms")

        // game loop
        while (true) {
            val loopStart = System.currentTimeMillis()
            desk = Desk(width, height, allPoints)

            val entityCount = with(br.nextLine()) {
                nextInt()
            }

            repeat(entityCount) {

                with(br.nextLine()) {
                    val x = nextInt()
                    val y = nextInt() // grid coordinate
                    val type = nextToken() // WALL, ROOT, BASIC, TENTACLE, HARVESTER, SPORER, A, B, C, D
                    val owner = nextInt() // 1 if your organ, 0 if enemy organ, -1 if neither
                    val organId = nextInt() // id of this entity if it's an organ, 0 otherwise
                    val organDir = nextToken() // N,E,S,W or X if not an organ
                    val organParentId = nextInt()
                    val organRootId = nextInt()

                    desk.fill(x, y, type, owner, organId, organRootId, organParentId, organDir)
                }

            }

            with(br.nextLine()) {
                val myA = nextInt()
                val myB = nextInt()
                val myC = nextInt()
                val myD = nextInt() // your protein stock
                desk.myStock = ProteinStock(myA, myB, myC, myD)
            }

            with(br.nextLine()) {
                val oppA = nextInt()
                val oppB = nextInt()
                val oppC = nextInt()
                val oppD = nextInt() // opponent's protein stock
                desk.enemyStock = ProteinStock(oppA, oppB, oppC, oppD)
            }

            val requiredActionsCount = with(br.nextLine()) {
                nextInt() // your number of organisms, output an action for each one in any order
            }

            logic.initBeforeMoves()
            for (i in 0 until requiredActionsCount) {
                val move = logic.move(i)
                println(move)
            }
            val loopStop = System.currentTimeMillis()
            log("In loop ${loopStop - loopStart} ms")
            logFlush()
        }
    }
}

fun main() {
    log("silver-arena-6-rc")
    mainLoop()
}

