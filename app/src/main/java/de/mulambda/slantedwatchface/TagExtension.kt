package de.mulambda.slantedwatchface

inline fun <reified T> T.TAG(): String = T::class.qualifiedName ?: T::class.java.name