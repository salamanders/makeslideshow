package info.benjaminhill.util

import java.lang.management.ManagementFactory
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration


@ExperimentalTime
class BasicLogger {
    var level = LEVEL.CONFIG
    private val jvmStartTime = ManagementFactory.getRuntimeMXBean().startTime

    private fun ts() = (System.currentTimeMillis() - jvmStartTime).toDuration(DurationUnit.MILLISECONDS).toString()

    fun log(message: () -> String, thisMessageLevel: LEVEL) = when (thisMessageLevel) {
        !in level..LEVEL.SEVERE -> {
            // discard
        }
        in LEVEL.FINEST..LEVEL.INFO -> println("${ts()} $thisMessageLevel: ${message()}")
        else -> System.err.println("${ts()} $thisMessageLevel: ${message()}")
    }

    fun finest(message: () -> String) = this.log(message, LEVEL.FINEST)
    fun finer(message: () -> String) = this.log(message, LEVEL.FINER)
    fun fine(message: () -> String) = this.log(message, LEVEL.FINE)
    fun config(message: () -> String) = this.log(message, LEVEL.CONFIG)
    fun info(message: () -> String) = this.log(message, LEVEL.INFO)
    fun warning(message: () -> String) = this.log(message, LEVEL.WARNING)
    fun severe(message: () -> String) = this.log(message, LEVEL.SEVERE)

    companion object {
        enum class LEVEL {
            ALL,
            FINEST,
            FINER,
            FINE,
            CONFIG,
            INFO,
            WARNING,
            SEVERE
        }
    }
}