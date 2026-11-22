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
                val factor = detector.scaleFactor.to
