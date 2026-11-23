package ru.taxichurkin.engine

import kotlin.math.*

class ChurkinCameraBounds(
    private val rings: List<List<Pair<Double, Double>>>
) {

    data class Point(
        val x: Double,
        val y: Double
    )

    data class CameraOffset(
        val x: Double,
        val y: Double
    )

    // Проверяем, находится ли точка внутри района
    fun contains(point: Point): Boolean {
        var inside = false

        for (ring in rings) {
            if (ring.size < 3) continue

            var j = ring.lastIndex

            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[j]

                if ((a.second > point.y) !=
                    (b.second > point.y)
                ) {
                    val intersectionX =
                        (b.first - a.first) *
                        (point.y - a.second) /
                        (b.second - a.second) +
                        a.first

                    if (point.x < intersectionX) {
                        inside = !inside
                    }
                }

                j = i
            }
        }

        return inside
    }

    // Находим ближайшую точку границы
    fun constrain(point: Point): Point {
        if (contains(point)) return point

        var nearest = point
        var bestDistance = Double.POSITIVE_INFINITY

        for (ring in rings) {
            if (ring.size < 2) continue

            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]

                val dx = b.first - a.first
                val dy = b.second - a.second

                val lengthSquared = dx * dx + dy * dy

                if (lengthSquared == 0.0) continue

                val t = (
                    ((point.x - a.first) * dx +
                     (point.y - a.second) * dy) /
                    lengthSquared
                ).coerceIn(0.0, 1.0)

                val x = a.first + t * dx
                val y = a.second + t * dy

                val distance =
                    (point.x - x).pow(2) +
                    (point.y - y).pow(2)

                if (distance < bestDistance) {
                    bestDistance = distance
                    nearest = Point(x, y)
                }
            }
        }

        return nearest
    }

    // Ограничиваем перемещение карты краями экрана
    fun constrainViewport(
        projectedRings: List<List<Point>>,
        screenWidth: Int,
        screenHeight: Int,
        offsetX: Double,
        offsetY: Double
    ): CameraOffset {

        if (screenWidth <= 0 || screenHeight <= 0) {
            return CameraOffset(offsetX, offsetY)
        }

        val points = projectedRings.flatten()

        if (points.isEmpty()) {
            return CameraOffset(offsetX, offsetY)
        }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val screenW = screenWidth.toDouble()
        val screenH = screenHeight.toDouble()

        val mapW = maxX - minX
        val mapH = maxY - minY

        val newX = if (mapW >= screenW) {
            offsetX.coerceIn(
                screenW - maxX,
                -minX
            )
        } else {
            screenW / 2.0 - (minX + maxX) / 2.0
        }

        val newY = if (mapH >= screenH) {
            offsetY.coerceIn(
                screenH - maxY,
                -minY
            )
        } else {
            screenH / 2.0 - (minY + maxY) / 2.0
        }

        return CameraOffset(
            x = newX,
            y = newY
        )
    }
}
