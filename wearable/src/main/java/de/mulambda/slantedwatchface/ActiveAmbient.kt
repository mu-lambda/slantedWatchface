package de.mulambda.slantedwatchface

data class ActiveAmbient<T>(val active: T, val ambient: T) {
    fun get(isAmbient: Boolean) = if (isAmbient) ambient else active
}