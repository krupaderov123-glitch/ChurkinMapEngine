package ru.taxichurkin.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONObject
import kotlin.math.*

class ChurkinMapEngine(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val backgroundColor = Color.rgb(24, 26, 32)
    private val gridColor = Color.rgb(65, 70, 80)
    private val borderColor = Color.rgb(255, 215, 0)
    private val fillColor = Color.argb(65, 255, 215, 0)

    private var zoom = 16.0

    private var offsetX = 0.0
    private var offsetY = 0.0

    private var lastX = 0f
    private var lastY = 0f

    private val baseGridSize = 100.0

    private val centerLat = 43.095
    private val centerLng = 131.930

    private val boundary =
        mutableListOf<List<Pair<Double, Double>>>()

    private var loadError: String? = null

    init {
        loadBoundary()
    }

    private fun loadBoundary() {
        try {
            val json = context.assets.open(
                "churkin_limit.geojson"
            ).bufferedReader().use {
                it.readText()
            }

            val root = JSONObject(json)
            val features = root.getJSONArray("features")

            for (i in 0 until features.length()) {
                val geometry = features
                    .getJSONObject(i)
                    .getJSONObject("geometry")

                if (geometry.getString("type") != "Polygon") {
                    continue
                }

                val rings = geometry.getJSONArray("coordinates")

                for (r in 0 until rings.length()) {
                    val coordinates = rings.getJSONArray(r)
                    val points =
                        mutableListOf<Pair<Double, Double>>()

                    for (j in 0 until coordinates.length()) {
                        val point = coordinates.getJSONArray(j)

                        points.add(
                            Pair(
                                point.getDouble(0),
                                point.getDouble(1)
                            )
                        )
                    }

                    if (points.size >= 3) {
                        boundary.add(points)
                    }
                }
            }

        } catch (e: Exception) {
            loadError = e.message ?: "GeoJSON error"
        }
    }

    private fun mercatorY(lat: Double): Double {
        val radians = Math.toRadians(
            lat.coerceIn(-85.05112878, 85.05112878)
        )

        return ln(tan(Math.PI / 4.0 + radians / 2.0))
    }

    private fun projectX(lng: Double): Double {
        val scale = 256.0 * 2.0.pow(zoom)

        return width / 2.0 +
            (lng - centerLng) / 360.0 * scale +
            offsetX
    }

    private fun projectY(lat: Double): Double {
        val scale = 256.0 * 2.0.pow(zoom)

        return height / 2.0 -
            (mercatorY(lat) - mercatorY(centerLat)) *
            scale / (2.0 * Math.PI) +
            offsetY
    }

    // Проверяем, находится ли точка внутри полигона
    private fun isInsideBoundary(
        x: Double,
        y: Double
    ): Boolean {

        var inside = false

        for (ring in boundary) {
            var j = ring.size - 1

            for (i in ring.indices) {

                val xi = projectX(ring[i].first)
                val yi = projectY(ring[i].second)

                val xj = projectX(ring[j].first)
                val yj = projectY(ring[j].second)

                val intersects =
                    ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) /
                        (yj - yi) + xi)

                if (intersects) {
                    inside = !inside
                }

                j = i
            }
        }

        return inside
    }

    // Проверяем новое смещение до применения
    private fun canMove(
        newOffsetX: Double,
        newOffsetY: Double
    ): Boolean {

        val oldX = offsetX
        val oldY = offsetY

        offsetX = newOffsetX
        offsetY = newOffsetY

        val allowed = isInsideBoundary(
            width / 2.0,
            height / 2.0
        )

        offsetX = oldX
        offsetY = oldY

        return allowed
    }

    private fun moveMap(dx: Double, dy: Double) {

        val newX = offsetX + dx
        val newY = offsetY + dy

        if (canMove(newX, newY)) {
            offsetX = newX
            offsetY = newY
        } else {
            // Проверяем движение по каждой оси отдельно
            if (canMove(newX, offsetY)) {
                offsetX = newX
            }

            if (canMove(offsetX, newY)) {
                offsetY = newY
            }
        }

        invalidate()
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {

                val factor = detector.scaleFactor.toDouble()

                if (!factor.isFinite() || factor <= 0.0) {
                    return false
                }

                val oldZoom = zoom
                val oldOffsetX = offsetX
                val oldOffsetY = offsetY

                zoom = (
                    zoom + ln(factor) / ln(2.0)
                ).coerceIn(14.5, 19.0)

                val actualFactor = 2.0.pow(zoom - oldZoom)

                val focusX = detector.focusX.toDouble()
                val focusY = detector.focusY.toDouble()

                val newOffsetX =
                    focusX + (oldOffsetX - focusX) * actualFactor

                val newOffsetY =
                    focusY + (oldOffsetY - focusY) * actualFactor

                // Масштаб уже изменён — проверяем новые координаты
                if (canMove(newOffsetX, newOffsetY)) {
                    offsetX = newOffsetX
                    offsetY = newOffsetY
                } else {
                    // Сохраняем центр в допустимой области
                    offsetX = oldOffsetX * actualFactor
                    offsetY = oldOffsetY * actualFactor
                }

                invalidate()
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(backgroundColor)

        drawGrid(canvas)
        drawBoundary(canvas)
        drawCenter(canvas)
        drawInfo(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = baseGridSize * 2.0.pow(zoom - 16.0)

        paint.color = gridColor
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE

        var x = offsetX % gridSize

        while (x < width) {
            canvas.drawLine(
                x.toFloat(), 0f,
                x.toFloat(), height.toFloat(),
                paint
            )
            x += gridSize
        }

        var y = offsetY % gridSize

        while (y < height) {
            canvas.drawLine(
                0f, y.toFloat(),
                width.toFloat(), y.toFloat(),
                paint
            )
            y += gridSize
        }

        paint.style = Paint.Style.FILL
    }

    private fun drawBoundary(canvas: Canvas) {
        if (boundary.isEmpty()) return

        val path = Path()

        for (ring in boundary) {
            ring.forEachIndexed { index, point ->

                val x = projectX(point.first).toFloat()
                val y = projectY(point.second).toFloat()

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            path.close()
        }

        path.fillType = Path.FillType.EVEN_ODD

        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = borderColor
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
    }

    private fun drawCenter(canvas: Canvas) {
        paint.color = Color.YELLOW

        canvas.drawCircle(
            width / 2f,
            height / 2f,
            12f,
            paint
        )
    }

    private fun drawInfo(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.textSize = 36f

        canvas.drawText(
            "ChurkinMapEngine",
            40f, 80f, paint
        )

        canvas.drawText(
            "Zoom: %.1f".format(zoom),
            40f, 130f, paint
        )

        paint.textSize = 27f

        canvas.drawText(
            "Boundary rings: ${boundary.size}",
            40f, 175f, paint
        )

        paint.color = if (
            boundary.isNotEmpty() &&
            isInsideBoundary(width / 2.0, height / 2.0)
        ) Color.GREEN else Color.RED

        canvas.drawText(
            if (boundary.isNotEmpty() &&
                isInsideBoundary(width / 2.0, height / 2.0)
            ) "INSIDE CHURKIN"
            else "OUTSIDE CHURKIN",
            40f, 215f, paint
        )

        if (loadError != null) {
            paint.color = Color.RED
            canvas.drawText(
                "GeoJSON loading error",
                40f, 255f, paint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (
                    event.pointerCount == 1 &&
                    !scaleDetector.isInProgress
                ) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    lastX = event.x
                    lastY = event.y

                    moveMap(
                        dx.toDouble(),
                        dy.toDouble()
                    )
                }

                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    val remainingIndex =
                        if (event.actionIndex == 0) 1 else 0

                    lastX = event.getX(remainingIndex)
                    lastY = event.getY(remainingIndex)
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                return true
            }
        }

        return true
    }
}
