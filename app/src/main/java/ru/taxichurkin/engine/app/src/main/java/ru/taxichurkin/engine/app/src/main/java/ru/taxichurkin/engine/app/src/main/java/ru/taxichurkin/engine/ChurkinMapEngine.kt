package ru.taxichurkin.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class ChurkinMapEngine(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Центр Чуркина
    private var centerLat = 43.095
    private var centerLng = 131.930

    // Масштаб карты
    private var zoom = 16.0

    private var lastX = 0f
    private var lastY = 0f

    private val backgroundColor =
        Color.rgb(24, 26, 32)

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {

                val factor = detector.scaleFactor.toDouble()

                zoom = (
                    zoom + kotlin.math.ln(factor) /
                    kotlin.math.ln(2.0)
                ).coerceIn(14.5, 19.0)

                invalidate()
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Тёмный фон нашей карты
        canvas.drawColor(backgroundColor)

        // Временная отметка центра Чуркина
        paint.color = Color.YELLOW
        paint.strokeWidth = 4f

        val cx = width / 2f
        val cy = height / 2f

        canvas.drawCircle(cx, cy, 12f, paint)

        paint.textSize = 36f
        paint.color = Color.WHITE

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

                if (!scaleDetector.isInProgress &&
                    event.pointerCount == 1
                ) {

                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    lastX = event.x
                    lastY = event.y

                    // Временная логика перемещения.
                    // Позже заменим на точные координаты.
                    centerLng -= dx * 0.00001
                    centerLat += dy * 0.00001

                    invalidate()
                }

                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                return true
            }

            MotionEvent.ACTION_UP -> {
                return true
            }
        }

        return true
    }
}
