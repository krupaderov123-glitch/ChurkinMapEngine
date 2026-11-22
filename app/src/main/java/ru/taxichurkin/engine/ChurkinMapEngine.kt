package ru.taxichurkin.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.ln

class ChurkinMapEngine(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerLat = 43.095
    private var centerLng = 131.930
    private var zoom = 16.0

    private var lastX = 0f
    private var lastY = 0f

    private val backgroundColor = Color.rgb(24, 26, 32)

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {

                val factor = detector.scaleFactor.toDouble()

                if (factor <= 0.0 || !factor.isFinite()) {
                    return false
                }

                zoom = (
                    zoom + ln(factor) / ln(2.0)
                ).coerceIn(14.5, 19.0)

                invalidate()
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(backgroundColor)

        val cx = width / 2f
        val cy = height / 2f

        paint.color = Color.YELLOW
        canvas.drawCircle(cx, cy, 12f, paint)

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

                    lastX = event.x
                    lastY = event.y

                    centerLng -= dx * 0.00001
                    centerLat += dy * 0.00001

                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
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
