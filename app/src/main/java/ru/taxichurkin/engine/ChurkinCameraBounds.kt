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
    private var minimumZoom = 15.5

    private var offsetX = 0.0
    private var offsetY = 0.0

    private var lastX = 0f
    private var lastY = 0f

    private val centerLat = 43.095
    private val centerLng = 131.930

    private val baseGridSize = 100.0

    private val boundary =
        mutableListOf<List<Pair<Double, Double>>>()

    private var cameraBounds: ChurkinCameraBounds? = null

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

            if (boundary.isNotEmpty()) {
                cameraBounds = ChurkinCameraBounds(boundary)
            }

        } catch (e: Exception) {
            loadError = e.message ?: "GeoJSON error"
        }
    }

    private fun mercatorY(lat: Double): Double {
        val radians = Math.toRadians(
            lat.coerceIn(-85.05112878, 85.05112878)
        )

        return ln(
            tan(Math.PI / 4.0 + radians / 2.0)
        )
    }

    private fun mapScale(): Double {
        return 256.0 * 2.0.pow(zoom)
    }

    private fun projectX(
        lng: Double,
        cameraX: Double = offsetX
    ): Double {
        return width / 2.0 +
            (lng - centerLng) / 360.0 * mapScale() +
            cameraX
    }

    private fun projectY(
        lat: Double,
        cameraY: Double = offsetY
    ): Double {
        return height / 2.0 -
            (mercatorY(lat) - mercatorY(centerLat)) *
            mapScale() / (2.0 * Math.PI) +
            cameraY
    }

    private fun projectedBoundary():
        List<List<ChurkinCameraBounds.Point>> {

        return boundary.map { ring ->
            ring.map { point ->
                ChurkinCameraBounds.Point(
                    projectX(point.first, 0.0),
                    projectY(point.second, 0.0)
                )
            }
        }
    }

    private fun calculateMinimumZoom() {

        if (
            boundary.isEmpty() ||
            width <= 0 ||
            height <= 0
        ) return

        var minLng = Double.POSITIVE_INFINITY
        var maxLng = Double.NEGATIVE_INFINITY

        var minMercY = Double.POSITIVE_INFINITY
        var maxMercY = Double.NEGATIVE_INFINITY

        for (ring in boundary) {
            for (point in ring) {

                minLng = min(minLng, point.first)
                maxLng = max(maxLng, point.first)

                val y = mercatorY(point.second)

                minMercY = min(minMercY, y)
                maxMercY = max(maxMercY, y)
            }
        }

        val longitudeSpan = maxLng - minLng
        val mercatorSpan = maxMercY - minMercY

        if (
            longitudeSpan <= 0.0 ||
            mercatorSpan <= 0.0
        ) return

        // Район должен быть достаточно крупным
        // хотя бы по одному измерению.
        val scaleForWidth =
            width * 0.90 * 360.0 / longitudeSpan

        val scaleForHeight =
            height * 0.90 * 2.0 *
            Math.PI / mercatorSpan

        val requiredScale = max(
            scaleForWidth,
            scaleForHeight
        )

        minimumZoom = (
            ln(requiredScale / 256.0) / ln(2.0)
        ).coerceIn(14.5, 19.0)

        zoom = zoom.coerceIn(minimumZoom, 19.0)
    }

    private fun centerBoundary() {

        if (boundary.isEmpty()) return

        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY

        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (ring in projectedBoundary()) {
            for (point in ring) {
                minX = min(minX, point.x)
                maxX = max(maxX, point.x)
                minY = min(minY, point.y)
                maxY = max(maxY, point.y)
            }
        }

        offsetX = width / 2.0 - (minX + maxX) / 2.0
        offsetY = height / 2.0 - (minY + maxY) / 2.0
    }

    // Ограничение относительно краёв экрана.
    private fun constrainCamera() {

        val bounds = cameraBounds ?: return

        if (width <= 0 || height <= 0) return

        val result = bounds.constrainViewport(
            projectedRings = projectedBoundary(),
            screenWidth = width,
            screenHeight = height,
            offsetX = offsetX,
            offsetY = offsetY
        )

        offsetX = result.x
        offsetY = result.y
    }

    private fun moveMap(
        dx: Double,
        dy: Double
    ) {
        offsetX += dx
        offsetY += dy

        constrainCamera()
        invalidate()
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {

                val factor = detector.scaleFactor.toDouble()

                if (
                    !factor.isFinite() ||
                    factor <= 0.0
                ) return false

                val oldZoom = zoom

                zoom = (
                    zoom + ln(factor) / ln(2.0)
                ).coerceIn(minimumZoom, 19.0)

                val actualFactor =
                    2.0.pow(zoom - oldZoom)

                val focusX =
                    detector.focusX.toDouble()

                val focusY =
                    detector.focusY.toDouble()

                offsetX =
                    (offsetX + width / 2.0 - focusX) *
                    actualFactor +
                    focusX - width / 2.0

                offsetY =
                    (offsetY + height / 2.0 - focusY) *
                    actualFactor +
                    focusY - height / 2.0

                constrainCamera()

                invalidate()
                return true
            }
        }
    )

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        calculateMinimumZoom()
        centerBoundary()
        constrainCamera()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(backgroundColor)

        drawGrid(canvas)
        drawBoundary(canvas)
        drawInfo(canvas)
    }

    private fun drawGrid(canvas: Canvas) {

        val gridSize =
            baseGridSize * 2.0.pow(zoom - 16.0)

        paint.color = gridColor
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE

        var x = offsetX % gridSize

        while (x < width) {
            canvas.drawLine(
                x.toFloat(),
                0f,
                x.toFloat(),
                height.toFloat(),
                paint
            )

            x += gridSize
        }

        var y = offsetY % gridSize

        while (y < height) {
            canvas.drawLine(
                0f,
                y.toFloat(),
                width.toFloat(),
                y.toFloat(),
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

                val x =
                    projectX(point.first).toFloat()

                val y =
                    projectY(point.second).toFloat()

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

    private fun drawInfo(canvas: Canvas) {

        paint.color = Color.WHITE
        paint.textSize = 36f

        canvas.drawText(
            "ChurkinMapEngine",
            40f,
            80f,
            paint
        )

        canvas.drawText(
            "Zoom: %.1f".format(zoom),
            40f,
            130f,
            paint
        )

        paint.textSize = 27f

        canvas.drawText(
            "Min zoom: %.1f".format(minimumZoom),
            40f,
            175f,
            paint
        )

        canvas.drawText(
            "Boundary rings: ${boundary.size}",
            40f,
            215f,
            paint
        )

        if (loadError != null) {
            paint.color = Color.RED

            canvas.drawText(
                "GeoJSON loading error",
                40f,
                255f,
                paint
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
