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

    override fun toString(): String {
        return "[$a,$b,$c,$d]"
    }

    companion object {
        val ROOT = ProteinStock(1, 1, 1, 1)
        val BASIC = ProteinStock(1, 0, 0, 0)
        val HARVESTER = ProteinStock(0, 0, 1, 1)
        val TENTACLE = ProteinStock(0, 1, 1, 0)
        val SPORER = ProteinStock(0, 1, 0, 1)
        val SPORE_LIMIT = ProteinStock(2, 2, 2, 2)
        val IDLE_HARV_LIMIT = ProteinStock(0, 0, 2, 2)
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

    fun getEnemyOrgans(): Sequence<GridPoint> = allPoints.asSequence().filter { isOrgan(it) && isEnemy(it) }

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
    fun isEnemyTentacle(point: GridPoint): Boolean =
        desk.inbound(point) && desk.isEnemy(point) && desk.isTentacle(point)

    fun isOrgan(point: GridPoint): Boolean =
        grid[point.y][point.x] == Item.ROOT || grid[point.y][point.x] == Item.BASIC || grid[point.y][point.x] == Item.HARVESTER || grid[point.y][point.x] == Item.TENTACLE || grid[point.y][point.x] == Item.SPORER

    fun isRoot(point: GridPoint): Boolean = grid[point.y][point.x] == Item.ROOT
    fun isSporer(point: GridPoint): Boolean = grid[point.y][point.x] == Item.SPORER
    fun isHarvester(point: GridPoint): Boolean = grid[point.y][point.x] == Item.HARVESTER
    fun isTentacle(point: GridPoint): Boolean = grid[point.y][point.x] == Item.TENTACLE
    fun isMy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ME
    fun isReallyMy(point: GridPoint, organRootId: Int): Boolean = isMy(point) && organRootId == organRootId(point)
    fun isEnemy(point: GridPoint): Boolean = meOrEnemy[point.y][point.x] == MeOrEnemy.ENEMY
    fun isEnemyTentalce(point: GridPoint): Boolean = isEnemy(point) && isTentacle(point)
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

fun dirPointFromDiff(from: GridPoint, to: GridPoint?): GridPoint = if (to != null) {
    normalizeDirPoint(to - from)
} else {
    Desk.NORTH
}

sealed interface Move {

    fun toProtocolMove(): String

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

    class Harvester(val organFrom: GridPoint, val growTo: GridPoint, val forSource: GridPoint?) : Move {
        override fun toProtocolMove(): String {
            desk.myStock -= ProteinStock.HARVESTER
            val dir = dirPointFromDiff(growTo, forSource)
            val dirChar = dirCharByDirPoint(dir)
            val organId = desk.organId(organFrom)
            val xTo = growTo.x
            val yTo = growTo.y
            return "GROW $organId $xTo $yTo HARVESTER $dirChar"
        }
    }

    class Sporer(val organFrom: GridPoint, val growTo: GridPoint, val forSource: GridPoint?) : Move {
        override fun toProtocolMove(): String {
            desk.myStock -= ProteinStock.SPORER
            val dir = dirPointFromDiff(growTo, forSource)
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

    class Tentacle(val organFrom: GridPoint, val growTo: GridPoint, val forVictim: GridPoint?) : Move {
        override fun toProtocolMove(): String {

            val dir = dirPointFromDiff(growTo, forVictim)
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

    fun line(currentRootOrganId: Int, from: GridPoint, dir: GridPoint): List<GridPoint> {
        val result = mutableListOf<GridPoint>()

        result.add(from)
        var pretender = from

        pretender += dir
        while (desk.inbound(pretender) && desk.isSpaceOrProtein(pretender)) {
            result.add(pretender)
            pretender += dir
        }

        if (desk.inbound(pretender) && desk.isSporer(pretender) && desk.isReallyMy(pretender, currentRootOrganId) ) {
            log("no shoot to sporer $pretender")
            result.clear()
            result += pretender
        }

        return result
    }

    fun doSpore(currentRootOrganId: Int): Move? {
        val sporeState = sporeStat.getOrPut(currentRootOrganId) { SporeState.NONE }

        if (!desk.myStock.enoughFor(ProteinStock.SPORE_LIMIT)) {
            log("spore limit")
            return null
        }

        when (sporeState) {
            SporeState.NONE -> {
                if (!desk.myStock.enoughFor(ProteinStock.SPORER + ProteinStock.ROOT)) {
                    log("no res for sporer and spore")
                    return null
                }
                val pretenders = desk.getMyOrgans(currentRootOrganId).asSequence().flatMap {
                        desk.neighbours(it).asSequence().filter { spaceOrUnusedProtein(it) }
                    }.toList()
                if (pretenders.isEmpty()) {
                    log("no room for sporer")
                    return null
                }

                val pretender = pretenders.asSequence().flatMap {
                    listOf(
                        line(currentRootOrganId, it, Desk.NORTH), line(currentRootOrganId, it, Desk.EAST), line(currentRootOrganId,it, Desk.WEST), line(currentRootOrganId,it, Desk.SOUTH)
                    )
                }.filter { it.size > 1 }.maxByOrNull { it.size }

                if (pretender == null) {
                    log("no good room for sporer")
                    return null
                }

                val organ = desk.neighbours(pretender.first()).asSequence().first {
                    desk.isReallyMy(it, currentRootOrganId) && desk.isOrgan(it)
                }


                log("set sporer from $organ to ${pretender.first()} for ${pretender.last()}")
                sporeStat[currentRootOrganId] = SporeState.SPORER
                return trySporer(organ, pretender.first(), pretender.last())
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

                val line = line(currentRootOrganId, sporer, desk.organDir(sporer))
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

    fun isSourceUnderHarvester(point: GridPoint): Boolean {
        check(desk.isProtein(point))
        return desk.neighbours(point).asSequence().any {
            desk.isHarvester(it) && desk.isMy(it) && (desk.organDir(it) + it == point)
        }
    }

    fun spaceOrUnusedProtein(it: GridPoint): Boolean = desk.isSpace(it) || (desk.isProtein(it) && !isSourceUnderHarvester(it))

    fun spaceOrProtein(it: GridPoint): Boolean = desk.isSpace(it) || desk.isProtein(it)

    fun doHarvFor(currentRootOrganId: Int, sourceChar: Char, sourceFun: (GridPoint) -> Boolean): Move? {

        if (harvProcess[sourceChar]!!) {
            log("work on $sourceChar")
            return null
        }

        val needProteinState = isNeedProteinSource(sourceChar, sourceFun)
        when(needProteinState) {
            NeedProtein.HAS_HARV -> {
                log("${NeedProtein.HAS_HARV} $sourceChar")
                return null
            }
            NeedProtein.NO_RES -> {
                log("${NeedProtein.NO_RES} $sourceChar")

                fun neededRes(point: GridPoint): Boolean = if (desk.myStock.c == 0) {
                    desk.isC(point)
                } else {
                    desk.isD(point)
                }

                val route = desk.getMyOrgans(currentRootOrganId).asSequence()
                    .flatMap { bfsTo(it, ::neededRes,  ::spaceOrUnusedProtein, 5)}
                    .minByOrNull { it.size }

                return if (route == null) {
                    log("no route for res $sourceChar")
                    null
                } else {
                    log("forced route for res $sourceChar from ${route.first()} to ${route[1]} cz ${route.last()}")
                    tryBasic(route.first(), route[1], true)
                }
            }
            NeedProtein.NEED_HARV -> {
                log("${NeedProtein.NEED_HARV} $sourceChar")
                val allAPretenders = desk.allPoints.asSequence().filter { sourceFun(it) }
                    .flatMap { desk.neighbours(it).filter { desk.isSpace(it) }.asSequence() }.toSet()

                val myOrgans = desk.getMyOrgans(currentRootOrganId)

                val paths = Path.minPathSeq(
                    myOrgans,
                    allAPretenders
                ) { desk.isSpace(it) || (!sourceFun(it) && spaceOrUnusedProtein(it)) }
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

                val fromOrgan = minPath.first()
                return if (minPath.size == 2) {
                    val pretender = minPath.last()
                    val aSource = desk.neighbours(pretender).asSequence().filter { sourceFun(it) }.first()
                    log("first $sourceChar harv")
                    tryHarvester(fromOrgan, pretender, aSource)
                } else {
                    // may be other resource?
                    val growTo = minPath[1]
                    if (desk.myStock.enoughFor(ProteinStock.IDLE_HARV_LIMIT)) {
                        val mayBeSource = desk.neighbours(growTo).asSequence().filter { desk.isProtein(it) }
                            .filter { !isSourceUnderHarvester(it) }.firstOrNull()
                        if (mayBeSource != null) {
                            log("goto $sourceChar harv, find other source")
                            tryHarvester(fromOrgan, growTo, mayBeSource)
                        } else {
                            log("just goto $sourceChar harv")
                            tryBasic(fromOrgan, growTo, true)
                        }
                    } else {
                        log("goto $sourceChar harv")
                        tryBasic(fromOrgan, growTo, true)
                    }
                }
            }
        }
    }

    fun bfsTo(from: GridPoint, target: (GridPoint) -> Boolean, allowedWalk: (GridPoint) -> Boolean, limit: Int? = null): List<List<GridPoint>> {
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

            if (target(lastElement)) {
                result += current
                continue
            }

            if (limit!= null && current.size > limit) {
                continue
            }

            desk.neighbours(lastElement).asSequence().filter { allowedWalk(it) || target(it) }.forEach { neighbour ->
                    queue.add(current + neighbour)
                }
        }

        return result
    }

    fun isInFrontOfEnemyTentacle(growTo: GridPoint): Boolean = inFrontOfEnemyTentacle(growTo) != null

    fun inFrontOfEnemyTentacle(growTo: GridPoint): GridPoint? = desk.neighbours(growTo).asSequence().firstOrNull {
        desk.isEnemyTentacle(it) && ((it + desk.organDir(it)) == growTo)
    }

    fun tryTentacle(organFrom: GridPoint, growTo: GridPoint, forVictim: GridPoint?): Move? {
        // in front of enemy tentacle?
        val inFrontOfEnemyTentacle = inFrontOfEnemyTentacle(growTo)

        if (inFrontOfEnemyTentacle != null) {
            log("ten in fr of e ten $inFrontOfEnemyTentacle")
            return null
        } else {
            return Move.Tentacle(organFrom, growTo, forVictim)
        }
    }

    fun tryHarvester(organFrom: GridPoint, growTo: GridPoint, forSource: GridPoint?): Move? {
        // in front of enemy tentacle?
        val inFrontOfEnemyTentacle = inFrontOfEnemyTentacle(growTo)

        if (inFrontOfEnemyTentacle != null) {
            log("harv in fr of e ten $inFrontOfEnemyTentacle")
            return null
        } else {
            return Move.Harvester(organFrom, growTo, forSource)
        }
    }

    fun trySporer(organFrom: GridPoint, growTo: GridPoint, forSource: GridPoint?): Move? {
        // in front of enemy tentacle?
        val inFrontOfEnemyTentacle = inFrontOfEnemyTentacle(growTo)

        if (inFrontOfEnemyTentacle != null) {
            log("spo in fr of e ten $inFrontOfEnemyTentacle")
            return null
        } else {
            return Move.Sporer(organFrom, growTo, forSource)
        }
    }

    fun tryBasic(organFrom: GridPoint, growTo: GridPoint, forceOthers: Boolean = false): Move? {
        // in front of enemy tentacle?
        val inFrontOfEnemyTentacle = inFrontOfEnemyTentacle(growTo)

        if (inFrontOfEnemyTentacle != null) {
            log("bas in fr of e ten $inFrontOfEnemyTentacle")
            return null
        } else {

            return if (desk.myStock.enoughFor(ProteinStock.BASIC)) {
                Move.Basic(organFrom, growTo)
            } else {
                when {
                    !forceOthers -> null.also { log("no res for basic path") }
                    desk.myStock.enoughFor(ProteinStock.TENTACLE) -> tryTentacle(
                        organFrom,
                        growTo,
                        null
                    ).also { log("use ten inst bas") }

                    desk.myStock.enoughFor(ProteinStock.HARVESTER) -> tryHarvester(
                        organFrom,
                        growTo,
                        null
                    ).also { log("use harv inst bas") }

                    desk.myStock.enoughFor(ProteinStock.SPORER) -> trySporer(
                        organFrom,
                        growTo,
                        null
                    ).also { log("use spr inst bas") }

                    else -> null.also { log("no res all for basic path") }
                }
            }
        }
    }

    fun GridPoint.level(): Int {
        val organParentId = desk.organParentId(this)
        if (0 == organParentId) {
            return 1
        } else {
            val parentOrgan = desk.allPoints.first { desk.organId(it) == organParentId }
            return 1 + parentOrgan.level()
        }
    }


    fun doTentacles2(currentRootOrganId: Int, sensivity: Int, logString: String, selector: (GridPoint) -> Boolean): Move? {

        if (!desk.myStock.enoughFor(ProteinStock.TENTACLE)) {
            log("no energy for t $logString")
            return null
        }

        val pathToEnemy = desk.getMyOrgans(currentRootOrganId).asSequence()
            .flatMap {
                bfsTo(it, selector, { spaceOrProtein(it) && !isInFrontOfEnemyTentacle(it) }, sensivity + 1).asSequence()
                    .filter { it.size > 2 }
                    .filter { it.size <= sensivity }
                    .filter { !isInFrontOfEnemyTentacle(it[1]) }
            }.minByOrNull { it.size * 10000 + it.last().level() }

        if (pathToEnemy == null) {
            log("spim - not need t $logString")
            return null
        } else {
            log("path to enemy $logString size ${pathToEnemy.size}")
        }

        val organ = pathToEnemy[0]
        val growTo = pathToEnemy[1]
        val toVictim = pathToEnemy[2]
        log("alarm $logString ten from $organ grow $growTo for $toVictim cz ${pathToEnemy.last()}")
        return tryTentacle(organ, growTo, toVictim)
    }

    fun List<GridPoint>.selectByDistToCenter(): GridPoint {
        check(this.isNotEmpty())
        return this.asSequence().minBy { desk.distToCenter(it) }
    }

    fun justGrow(currentOrganRootId: Int): Move? {
        // paths to enemy
        val path = desk.getMyOrgans(currentOrganRootId).asSequence().flatMap { bfsTo(it, desk::isEnemy, ::spaceOrUnusedProtein).asSequence() }
            .filter { it.size > 2 }.filter { !isInFrontOfEnemyTentacle(it[1]) }.filter { desk.isEnemy(it.last()) }
            .minByOrNull { it.size }

        val (organFrom, next) = if (path != null) {
            log("route  ${path.first()} -> ${path[1]} cz ${path.last()}")
            path.first() to path[1]
        } else {
            log("grow random")
            val organFrom = desk.getMyOrgans(currentOrganRootId).asSequence().filter {
                    desk.neighbours(it).any { spaceOrUnusedProtein(it) }
                }.firstOrNull()
            if (organFrom == null) {
                log("no organ for idle grow")
                return null
            }
            val next = desk.neighbours(organFrom).asSequence().filter { spaceOrUnusedProtein(it) }.first()
            organFrom to next
        }

        val mayBeProtein = desk.neighbours(next).asSequence().filter { desk.isProtein(it) }.firstOrNull()
        val canGrowHarvester = desk.myStock.enoughFor(ProteinStock.IDLE_HARV_LIMIT)
        val needGrowHarvester = if (mayBeProtein != null) {
            desk.neighbours(mayBeProtein).asSequence().any { desk.isHarvester(it) }.not()
        } else {
            false
        }
        return if (mayBeProtein != null && canGrowHarvester && needGrowHarvester) {
            log("idle harv")
            tryHarvester(organFrom, next, mayBeProtein).also { log("just harv") }
        } else {
            log("idle basic")
            tryBasic(organFrom, next).also { log("justGrow") }
        }
    }

    fun agressiveGrow(currentOrganRootId: Int): Move? {

        val pretenders = desk.getMyOrgans(currentOrganRootId).asSequence().filter {
            desk.neighbours(it).any { desk.isSpaceOrProtein(it) && !isInFrontOfEnemyTentacle(it) }
        }.toList()

        if (pretenders.isEmpty()) {
            log("agressive fail")
            return null
        }

        val organFrom = pretenders.selectByDistToCenter()
        val next = desk.neighbours(organFrom).asSequence()
            .filter { desk.isSpaceOrProtein(it) && !isInFrontOfEnemyTentacle(it) }.toList().random()
        return tryBasic(organFrom, next, true).also { log("aggrGrow") }
    }

    enum class NeedProtein {
        HAS_HARV, NEED_HARV, NO_RES
    }
    fun isNeedProteinSource(sourceChar: Char, sourceFun: (GridPoint) -> Boolean): NeedProtein {

        //@formatter:off
        val hasActiveHarv =
            desk.allPoints.asSequence()
                .filter { desk.isHarvester(it) && desk.isMy(it) }
                .filter {
                    val dir = desk.organDir(it)
                    val sourcePretender = it + dir
                    desk.inbound(sourcePretender) && sourceFun(sourcePretender)
                }
                .any()
        //@formatter:on

        if (hasActiveHarv) {
            log("has harv $sourceChar")
            return NeedProtein.HAS_HARV
        }

        if (!desk.myStock.enoughFor(ProteinStock.HARVESTER)) {
            log("no harv res, poor $sourceChar")
            return NeedProtein.NO_RES
        }

        log("need harv $sourceChar")
        return NeedProtein.NEED_HARV
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
        log("ROOT: $currentRootOrganId, stock: ${desk.myStock}")

        //@formatter:off
        val result =
            doTentacles2(currentRootOrganId, 6, "eten", desk::isEnemyTentacle) ?:
            doSpore(currentRootOrganId) ?:
            doTentacles2(currentRootOrganId, 6, "ereg", desk::isEnemy) ?:
            doHarvFor(currentRootOrganId, A_CHAR, desk::isA) ?:
            doHarvFor(currentRootOrganId, C_CHAR, desk::isC) ?:
            doHarvFor(currentRootOrganId, D_CHAR, desk::isD) ?:
            doHarvFor(currentRootOrganId, B_CHAR, desk::isB) ?:
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
    log("gold-arena-2") // prev  Rank 137 336;  Rank 155 373
    mainLoop()
}
