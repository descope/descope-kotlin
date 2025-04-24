package com.descope.internal.others

import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeSdk

internal val DescopeLogger?.isUnsafeEnabled
    get() = this?.unsafe == true

internal fun DescopeLogger?.error(message: String, vararg values: Any?) {
    this?.log(DescopeLogger.Level.Error, message, *values)
}

internal fun DescopeLogger?.info(message: String, vararg values: Any?) {
    this?.log(DescopeLogger.Level.Info, message, *values)
}

internal fun DescopeLogger?.debug(message: String, vararg values: Any?) {
    this?.log(DescopeLogger.Level.Debug, message, *values)
}

internal open class ConsoleLogger(level: Level, unsafe: Boolean) : DescopeLogger(level, unsafe) {
    override fun output(level: Level, message: String, values: List<Any>) {
        var text = "[${DescopeSdk.NAME}] $message"
        if (values.isNotEmpty()) {
            text += """ (${values.joinToString(", ") { v -> v.toString() }})"""
        }
        println(text)
    }
    
    companion object {
        val basic: ConsoleLogger = ConsoleLogger(level = Level.Info, unsafe = false)
        
        val debug: ConsoleLogger = object : ConsoleLogger(level = Level.Debug, unsafe = false) {
            override val unsafe: Boolean get() = isApplicationDebuggable
        }
        
        val unsafe: ConsoleLogger = ConsoleLogger(level = Level.Debug, unsafe = true)

        var isApplicationDebuggable = false
    }
}
