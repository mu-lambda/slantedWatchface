package de.mulambda.slantedwatchface

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

fun Path.rect(rect: Rect): Path {
    // start from middle of edge to get nice round corners.
    val xMidTopEdge = (rect.left + rect.right).toFloat() / 2
    val yMidTopEdge = rect.top.toFloat()
    moveTo(xMidTopEdge, yMidTopEdge)
    lineTo(rect.right.toFloat(), rect.top.toFloat())
    lineTo(rect.right.toFloat(), (rect.bottom).toFloat())
    lineTo(rect.left.toFloat(), (rect.bottom).toFloat())
    lineTo(rect.left.toFloat(), rect.top.toFloat())
    lineTo(xMidTopEdge, yMidTopEdge)
    return this
}

fun RectF.toIntRect(): Rect = Rect().also { this.round(it) }
