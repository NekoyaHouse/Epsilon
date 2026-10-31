package com.github.epsilon

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Constants {

    const val NAME: String = "Epsilon"

    @JvmField
    val MOD_ID: String = BuildConfig.MOD_ID

    @JvmField
    val VERSION: String = BuildConfig.VERSION

    @JvmField
    val LOGGER: Logger = LogManager.getLogger(NAME)

    @JvmField
    var skipTicks: Int = 0

}
