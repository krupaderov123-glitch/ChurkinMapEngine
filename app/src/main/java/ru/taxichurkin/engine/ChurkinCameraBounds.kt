package ru.taxichurkin.engine

import kotlin.math.abs
import kotlin.math.hypot

class ChurkinCameraBounds(
    private val rings: List<List<Pair<Double, Double>>>
) {

    data class Point(
        val x: Double,
        val y: Double
    )

    private val epsilon = 1e-9

    private fun isOnSegment(
        p: Point,
        a: Point,
        b: Point
    ): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy)

        if (length < epsilon) {
            return hypot(p.x - a.x, p.y - a.y) < epsilon
        }

        val cross = abs(
            (p.x - a.x) * dy -
            (p.y - a.y) * dx
        )

        if (cross > epsilon * length) return false

        val dot =
            (p.x - a.x) * dx +
            (p.y - a.y) * dy

        return dot >= -epsilon &&
            dot <= length * length + epsilon
    }

    fun contains(point: Point): Boolean {
        var inside = false

        for (ring in rings) {
            if (ring.size < 3) continue

            var j = ring.lastIndex

            for (i in ring.indices) {
                val a = Point(
                    ring[i].first,
                    ring[i].second
                )

                val b = Point(
                    ring[j].first,
                    ring[j].second
                )

                if (isOnSegment(point, a, b)) {
                    return true
                }

                if ((a.y > point.y) != (b.y > point.y)) {
                    val intersectionX =
                        a.x + (point.y - a.y) *
                        (b.x - a.x) / (b.y - a.y)

                    if (point.x < intersectionX) {
                        inside = !inside
                    }
                }

                j = i
            }
        }

        return inside
    }

    private fun closestPointOnSegment(
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
             (p.y - a.y) * dy) / lengthSquared
        ).coerceIn(0.0, 1.0)

        return Point(
            a.x + t * dx,
            a.y + t * dy
        )
    }

    fun constrain(point: Point): Point {
        if (contains(point)) return point

        var closest: Point? = null
        var bestDistance = Double.POSITIVE_INFINITY

        for (ring in rings) {
            if (ring.size < 2) continue

            for (i in ring.indices) {
                val a = Point(
                    ring[i].first,
                    ring[i].second
                )

                val next = ring[(i + 1) % ring.size]

                val b = Point(
                    next.first,
                    next.second
                )

                val candidate = closestPointOnSegment(
                    point,
                    a,
                    b
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

        return closest ?: point
    }
}
