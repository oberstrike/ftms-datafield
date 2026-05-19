package de.ma.ftms.core

internal fun hex(value: String): ByteArray =
    value
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .map { it.toInt(16).toByte() }
        .toByteArray()
