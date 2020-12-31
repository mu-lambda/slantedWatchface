package de.mulambda.slantedwatchface

@Suppress("unused")
inline fun <reified T> T.TAG(): String = T::class.qualifiedName ?: T::class.java.name