package com.github.epsilon.kernel.math

import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.entity.Entity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.min


object MathUtil {

    fun findMiddleValue(min: Float, max: Float): Float {
        return (min + max) / 2
    }

    fun clamp(num: Double, min: Double, max: Double): Double {
        return if (num < min) min else min(num, max)
    }

    fun getEyesPos(entity: Entity): Vec3d {
        return entity.pos.add(0.0, entity.getEyeHeight(entity.pose).toDouble(), 0.0)
    }

    context (NonNullContext)
    fun calculateAngle(to: Vec3d): FloatArray {
        return calculateAngle(getEyesPos(player), to)
    }

    fun calculateAngle(from: Vec3d, to: Vec3d): FloatArray {
        val difX = to.x - from.x
        val difY = (to.y - from.y) * -1.0
        val difZ = to.z - from.z
        val dist = MathHelper.sqrt((difX * difX + difZ * difZ).toFloat()).toDouble()
        val yD = MathHelper.wrapDegrees(Math.toDegrees(atan2(difZ, difX)) - 90.0).toFloat()
        val pD = MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(atan2(difY, dist))), -90.0, 90.0).toFloat()
        return floatArrayOf(yD, pD)
    }

    fun square(input: Double): Double {
        return input * input
    }

}