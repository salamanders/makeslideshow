package info.benjaminhill.util

import java.lang.management.ManagementFactory


class BasicLogger {
    var level = LEVEL.DEBUG
    private val jvmStartTime = ManagementFactory.getRuntimeMXBean().startTime
    private fun ts() = String.format("%09d", System.currentTimeMillis() - jvmStartTime)

    fun debug(message: () -> String) {
        if (level <= LEVEL.DEBUG) {
            println("${ts()} DEBUG: ${message()}")
        }
    }

    fun info(message: () -> String) {
        if (level <= LEVEL.INFO) {
            println("${ts()}  INFO: ${message()}")
        }
    }

    fun warn(message: () -> String) {
        if (level <= LEVEL.WARN) {
            System.err.println("${ts()}  WARN: ${message()}")
        }
    }

    fun error(message: () -> String) {
        if (level <= LEVEL.ERROR) {
            System.err.println("${ts()} ERROR: ${message()}")
        }
    }

    companion object {
        enum class LEVEL {
            DEBUG,
            INFO,
            WARN,
            ERROR
        }
    }
}