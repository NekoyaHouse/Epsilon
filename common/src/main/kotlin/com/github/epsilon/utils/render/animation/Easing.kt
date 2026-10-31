package com.github.epsilon.utils.render.animation

import net.minecraft.util.Mth
import java.util.function.Function
import kotlin.math.*

enum class Easing(val function: Function<Float, Float>) {

    LINEAR(Function<Float, Float> { x -> x }),
    DECELERATE(Function<Float, Float> { x -> 1.0f - ((x - 1.0f) * (x - 1.0f)) }),
    SMOOTH_STEP(Function<Float, Float> { x -> (-2.0 * (x.toDouble()).pow(3.0) + 3.0 * (x.toDouble()).pow(2.0)).toFloat() }),
    EASE_IN_QUAD(Function<Float, Float> { x -> x * x }),
    EASE_OUT_QUAD(Function<Float, Float> { x -> x * (2.0f - x) }),
    EASE_IN_OUT_QUAD(Function<Float, Float> { x -> if (x < 0.5f) 2.0f * x * x else -1.0f + (4.0f - 2.0f * x) * x }),
    EASE_IN_CUBIC(Function<Float, Float> { x -> x * x * x }),
    EASE_OUT_CUBIC(Function<Float, Float> { x ->
        val v = x - 1.0f
        v * v * v + 1.0f
    }),
    EASE_IN_OUT_CUBIC(Function<Float, Float> { x ->
        if (x < 0.5f) 4.0f * x * x * x else (x - 1.0f) * (2.0f * x - 2.0f) * (2.0f * x - 2.0f) + 1.0f
    }),
    EASE_IN_QUART(Function<Float, Float> { x -> x * x * x * x }),
    EASE_OUT_QUART(Function<Float, Float> { x ->
        val v = x - 1.0f
        1.0f - v * v * v * v
    }),
    EASE_IN_OUT_QUART(Function<Float, Float> { x ->
        if (x < 0.5f) {
            8.0f * x * x * x * x
        } else {
            val v = x - 1.0f
            1.0f - 8.0f * v * v * v * v
        }
    }),
    EASE_IN_QUINT(Function<Float, Float> { x -> x * x * x * x * x }),
    EASE_OUT_QUINT(Function<Float, Float> { x ->
        val v = x - 1.0f
        1.0f + v * v * v * v * v
    }),
    EASE_IN_OUT_QUINT(Function<Float, Float> { x ->
        if (x < 0.5f) {
            16.0f * x * x * x * x * x
        } else {
            val v = x - 1.0f
            1.0f + 16.0f * v * v * v * v * v
        }
    }),
    EASE_IN_SINE(Function<Float, Float> { x -> 1.0f - Mth.cos((x.toDouble() * PI * 0.5).toFloat().toDouble()) }),
    EASE_OUT_SINE(Function<Float, Float> { x -> Mth.sin((x.toDouble() * PI * 0.5).toFloat().toDouble()) }),
    EASE_IN_OUT_SINE(Function<Float, Float> { x -> 1.0f - Mth.cos((PI * x.toDouble() * 0.5).toFloat().toDouble()) }),
    EASE_IN_EXPO(Function<Float, Float> { x -> if (x == 0.0f) 0.0f else (2.0).pow((10.0f * x - 10.0f).toDouble()).toFloat() }),
    EASE_OUT_EXPO(Function<Float, Float> { x -> if (x == 1.0f) 1.0f else 1.0f - (2.0).pow((-10.0f * x).toDouble()).toFloat() }),
    EASE_IN_OUT_EXPO(Function<Float, Float> { x ->
        when {
            x == 0.0f -> 0.0f
            x == 1.0f -> 1.0f
            x < 0.5f -> (2.0).pow((20.0f * x - 10.0f).toDouble()).toFloat() * 0.5f
            else -> (2.0f - (2.0).pow((-20.0f * x + 10.0f).toDouble()).toFloat()) * 0.5f
        }
    }),
    EASE_IN_CIRC(Function<Float, Float> { x -> 1.0f - sqrt((1.0f - x * x).toDouble()).toFloat() }),
    EASE_OUT_CIRC(Function<Float, Float> { x ->
        val v = x - 1.0f
        sqrt((1.0f - v * v).toDouble()).toFloat()
    }),
    EASE_IN_OUT_CIRC(Function<Float, Float> { x ->
        if (x < 0.5f) {
            (1.0f - sqrt((1.0f - 4.0f * x * x).toDouble()).toFloat()) * 0.5f
        } else {
            (sqrt((1.0f - 4.0f * (x - 1.0f) * x).toDouble()).toFloat() + 1.0f) * 0.5f
        }
    }),
    SIGMOID(Function<Float, Float> { x -> 1.0f / (1.0f + exp((-x).toDouble()).toFloat()) }),
    EASE_OUT_ELASTIC(Function<Float, Float> { x ->
        when {
            x == 0.0f -> 0.0f
            x == 1.0f -> 1.0f
            else -> (
                (2.0).pow((-10.0f * x).toDouble())
                    * sin((x * 10.0f - 0.75) * ((2 * PI) / 3))
                    * 0.5f + 1.0f
                ).toFloat()
        }
    }),
    EASE_IN_BACK(Function<Float, Float> { x -> (1.70158f + 1.0f) * x * x * x - 1.70158f * x * x }),
    EASE_OUT_BACK(Function<Float, Float> { x ->
        val v = x - 1.0f
        1.0f + v * v * (2.70158f * v + 1.70158f)
    }),
    DYNAMIC_ISLAND(Function<Float, Float> { x ->
        (
            1.0 - cos(x.toDouble() * PI * (0.2 + 2.5 * (x.toDouble()).pow(3.0)))
                * exp((-x * 5.0f).toDouble())
            ).toFloat()
    });

}
