package de.mulambda.slantedwatchface

data class Active<T>(val active: T, val ambient: T) {
    fun get(isAmbient: Boolean) = if (isAmbient) ambient else active
}