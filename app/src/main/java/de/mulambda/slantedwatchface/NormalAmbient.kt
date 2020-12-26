package de.mulambda.slantedwatchface

data class NormalAmbient<T>(val normal: T, val ambient: T) {
    fun get(isAmbient: Boolean) = if (isAmbient) ambient else normal
}