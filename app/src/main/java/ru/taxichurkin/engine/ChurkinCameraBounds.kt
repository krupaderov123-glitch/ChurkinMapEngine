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

    // Проверка точки внутри границы района
    fun contains(point: Point): Boolean {

        var inside = false

        for (ring in rings) {

            if (ring.size < 3) continue

            var j = ring.size - 1

            for (i in ring.indices) {

                val xi = ring[i].first
                val yi = ring[i].second

                val xj = ring[j].first
                val yj = ring[j].second

                val intersects =
                    ((yi > point.y) != (yj > point.y)) &&
                    (point.x <
                        (xj - xi) *
                        (point.y - yi) /
                        (yj - yi) + xi)

                if (intersects) {
                    inside = !inside
                }

                j = i
            }
        }

        return inside
    }

    // Ограничение отдельной точки
    fun constrain(point: Point): Point {

        if (contains(point)) {
            return point
        }

        var nearest = point
        var bestDistance = Double.POSITIVE_INFINITY

        for (ring in rings) {

            if (ring.size < 2) continue

            for (i in ring.indices) {

                val a = ring[i]
                val b = ring[(i + 1) % ring.size]

                val dx = b.first - a.first
                val dy = b.second - a.second

                val lengthSquared =
                    dx * dx + dy * dy

                if (lengthSquared <= 0.0) continue

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

    // Ограничение движения карты краями экрана
    fun constrainViewport(
        projectedRings: List<List<Point>>,
        screenWidth: Int,
        screenHeight: Int,
        offsetX: Double,
        offsetY: Double
    ): CameraOffset {

        if (
            projectedRings.isEmpty() ||
            screenWidth <= 0 ||
            screenHeight <= 0
        ) {
            return CameraOffset(offsetX, offsetY)
        }

        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY

        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (ring in projectedRings) {

            for (point in ring) {

                minX = min(minX, point.x)
                maxX = max(maxX, point.x)

                minY = min(minY, point.y)
                maxY = max(maxY, point.y)
            }
        }

        if (
            !minX.isFinite() ||
            !maxX.isFinite() ||
            !minY.isFinite() ||
            !maxY.isFinite()
        ) {
            return CameraOffset(offsetX, offsetY)
        }

        val w = screenWidth.toDouble()
        val h = screenHeight.toDouble()

        val mapWidth = maxX - minX
        val mapHeight = maxY - minY

        val newX: Double
        val newY: Double

        // Горизонтальное ограничение
        newX = if (mapWidth >= w) {

            offsetX.coerceIn(
                w - maxX,
                -minX
            )

        } else {

            w / 2.0 - (minX + maxX) / 2.0
        }
