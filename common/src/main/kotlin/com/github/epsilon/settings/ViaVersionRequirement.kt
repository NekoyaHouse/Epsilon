package com.github.epsilon.settings

import com.github.epsilon.hooks.ViaFabricPlusHooks
import com.github.epsilon.hooks.ViaFabricPlusHooks.SupportVersion

enum class ViaVersionComparison(val symbol: String) {
    GREATER_THAN(">"), LESS_THAN("<"), EQUAL("="), GREATER_OR_EQUAL("≥"), LESS_OR_EQUAL("≤");

    fun matches(current: SupportVersion, required: SupportVersion): Boolean = when (this) {
        GREATER_THAN -> current > required
        LESS_THAN -> current < required
        EQUAL -> current == required
        GREATER_OR_EQUAL -> current >= required
        LESS_OR_EQUAL -> current <= required
    }
}

class ViaVersionRequirement(val comparison: ViaVersionComparison, version: String) {
    val version: SupportVersion = SupportVersion.parse(version)

    fun isSatisfied(): Boolean = ViaFabricPlusHooks.viaVersion?.let {
        comparison.matches(it, version)
    } ?: false

    fun displayName(): String = "Via ${comparison.symbol} $version"
}

fun <T : Setting<*>> T.viaOnly(comparison: ViaVersionComparison, version: String): T {
    viaRequirement = ViaVersionRequirement(comparison, version)
    return this
}
