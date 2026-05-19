package de.ma.ftms.bridge.debug

import android.util.Log

internal object BridgeLogger {
    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    fun info(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> if (throwable == null) Log.d(tag, message) else Log.d(tag, message, throwable)
            LogLevel.INFO -> if (throwable == null) Log.i(tag, message) else Log.i(tag, message, throwable)
            LogLevel.WARN -> if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
            LogLevel.ERROR -> if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
        }
    }
}
