/*
 * Copyright 2021-present The Slanted Watchface Authors. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
