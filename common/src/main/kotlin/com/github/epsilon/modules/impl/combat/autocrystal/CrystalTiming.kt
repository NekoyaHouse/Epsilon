package com.github.epsilon.modules.impl.combat.autocrystal

internal class CrystalTiming(
    private val nanoTime: () -> Long = System::nanoTime,
    private val wallTime: () -> Long = System::currentTimeMillis,
) {
    private var attackStart = nanoTime()
    private var placeStart = nanoTime()
    private var swapStart = nanoTime()
    var baseTicks = 0; private set
    private val attacked = HashMap<Int, Long>()
    private val attempts = HashMap<Int, Int>()
    private val placed = HashMap<Long, Long>()
    private var latencyTotal = 0L
    private var latencyCount = 0L

    val averageBreakMillis: Int get() = if (latencyCount == 0L) 0 else (latencyTotal / latencyCount).toInt()
    val attackElapsedMillis: Long get() = (nanoTime() - attackStart) / 1_000_000L

    fun attackReady(delay: Double): Boolean = attackElapsedMillis > delay.toLong()

    fun placeReady(delay: Int): Boolean = (nanoTime() - placeStart) / 1_000_000L > delay

    fun swapReady(penalty: Double): Boolean = (nanoTime() - swapStart) / 1_000_000L > (penalty * 25.0).toLong()

    fun tick() {
        baseTicks++
    }

    fun resetAttack() {
        attackStart = nanoTime()
    }

    fun resetPlace() {
        placeStart = nanoTime()
    }

    fun resetSwap() {
        swapStart = nanoTime()
    }

    fun resetBase() {
        baseTicks = 0
    }

    fun attackInterval(attackDelay: Double, attackFactor: Int): Double = attackDelay * (50.0 / maxOf(1, attackFactor))

    fun tickInterval(breakDelay: Int, timeout: Boolean, minimum: Double, extra: Double): Double =
        if (timeout) maxOf(minimum * 50.0, averageBreakMillis + extra * 50.0) else breakDelay.toDouble()

    fun recordAttack(id: Int) {
        attacked[id] = wallTime()
        attempts[id] = (attempts[id] ?: 0) + 1
    }

    fun recordPlace(pos: Long) {
        placed[pos] = wallTime()
    }

    fun onSpawn(pos: Long): Boolean = placed.remove(pos) != null

    fun blocksPlacement(id: Int, limit: Double): Boolean = (attempts[id] ?: 0) > limit * 10.0

    fun inhibited(id: Int): Boolean = attacked[id]?.let { it < averageBreakMillis } == true

    fun selectRepeated(id: Int, delay: Double) {
        if (attacked.remove(id) != null) {
            attackStart += (delay + 100.0).toLong()
        }
    }

    fun confirm(id: Int) {
        attempts.remove(id)
        attacked.remove(id)?.let {
            latencyTotal += (wallTime() - it).coerceAtLeast(0L)
            latencyCount++
        }
    }

    fun clear() {
        attacked.clear()
        attempts.clear()
        placed.clear()
        latencyTotal = 0L
        latencyCount = 0L
    }
}
