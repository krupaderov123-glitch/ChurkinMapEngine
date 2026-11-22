package ru.taxichurkin.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

class ChurkinMapEngine(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val backgroundColor = Color.rgb(24, 26, 32)
    private val gridColor = Color.rgb(65, 70, 80)

    private var zoom = 16.0

    // Смещение сетки в экранных пикселях
    private var offsetX = 0.0
    private var offsetY = 0.0

    private var lastX = 0f
    private var lastY = 0f

    // Размер клетки при масштабе 16
    private val baseGridSize = 100.0

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

                zoom = (
                    zoom + ln(factor) / ln(2.0)
                ).coerceIn(14.5, 19.0)

                val actualFactor = 2.0.pow(zoom - oldZoom)

                // Увеличиваем сетку относительно точки между пальцами
                val focusX = detector.focusX.toDouble()
                val focusY = detector.focusY.toDouble()

                offsetX = focusX + (offsetX - focusX) * actualFactor
                offsetY = focusY + (offsetY - focusY) * actualFactor

                invalidate()
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(backgroundColor)

        val gridSize = baseGridSize * 2.0.pow(zoom - 16.0)

        paint.color = gridColor
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE

        // Вертикальные линии
        val startX = offsetX % gridSize

        var x = startX

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

        // Горизонтальные линии
        val startY = offsetY % gridSize

        var y = startY

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

        // Центральная точка
        paint.color = Color.YELLOW

        canvas.drawCircle(
            width / 2f,
            height / 2f,
            12f,
            paint
        )

        // Информация
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

                    offsetX += dx
                    offsetY += dy

                    lastX = event.x
                    lastY = event.y

                    invalidate()
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
