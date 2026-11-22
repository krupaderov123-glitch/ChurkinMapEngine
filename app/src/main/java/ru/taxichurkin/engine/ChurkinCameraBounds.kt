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

    private val epsilon = 1e-9

    fun contains(point: Point): Boolean {
        var inside = false

        for (ring in rings) {
            if (ring.size < 3) continue

            var j = ring.lastIndex

            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[j]

                if (
                    pointOnSegment(
                        point.x, point.y,
                        a.first, a.second,
                        b.first, b.second
                    )
                ) {
                    return true
                }

                if ((a.second > point.y) != (b.second > point.y)) {
                    val intersectionX =
                        a.first +
                        (point.y - a.second) *
                        (b.first - a.first) /
                        (b.second - a.second)

                    if (point.x < intersectionX) {
                        inside = !inside
                    }
                }

                j = i
            }
        }

        return inside
    }

    private fun pointOnSegment(
        px: Double,
        py: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double
    ): Boolean {

        val dx = bx - ax
        val dy = by - ay

        val length = hypot(dx, dy)

        if (length < epsilon) {
            return hypot(px - ax, py - ay) < epsilon
        }

        val cross = abs(
            (px - ax) * dy -
            (py - ay) * dx
        )

        if (cross > epsilon * length) return false

        val dot =
            (px - ax) * dx +
            (py - ay) * dy

        return dot >= -epsilon &&
            dot <= length * length + epsilon
    }

    fun constrain(point: Point): Point {
        if (contains(point)) return point

        var closest = point
        var bestDistance = Double.POSITIVE_INFINITY

        for (ring in rings) {
            if (ring.size < 2) continue

            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]

                val candidate = closestPoint(
                    point,
                    Point(a.first, a.second),
                    Point(b.first, b.second)
                )

                val distance = hypot(
                    candidate.x - point.x,
                    candidate.y - point.y
                )

                if (distance < bestDistance) {
                    bestDistance = distance
                    closest = candidate
                }
            }
        }

        return closest
    }

    private fun closestPoint(
        p: Point,
        a: Point,
        b: Point
    ): Point {

        val dx = b.x - a.x
        val dy = b.y - a.y

        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared < epsilon * epsilon) {
            return a
        }

        val t = (
            ((p.x - a.x) * dx +
             (p.y - a.y) * dy) /
                lengthSquared
        ).coerceIn(0.0, 1.0)

        return Point(
            a.x + t * dx,
            a.y + t * dy
        )
    }

    /**
     * Ограничение карты по размерам экрана.
     *
     * projectedRings — координаты границы в пикселях
     * без текущего смещения камеры.
     *
     * offsetX / offsetY — запрошенное смещение.
     */
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

        val screenW = screenWidth.toDouble()
        val screenH = screenHeight.toDouble()

        // Мини
