package com.relascope.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.relascope.app.SignalState

class OverlayView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var k = 0f
    var state = SignalState.NONE
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (k <= 0f) return
        val side = k * width
        val r = RectF(width / 2 - side / 2, height / 2 - side / 2, width / 2 + side / 2, height / 2 + side / 2)
        when (state) {
            SignalState.COUNT -> { pFill.color = Color.argb(90, 0, 200, 0); pStroke.color = Color.GREEN }
            SignalState.NOCOUNT -> { pFill.color = Color.argb(50, 200, 0, 0); pStroke.color = Color.RED }
            SignalState.REPEAT -> { pFill.color = Color.argb(90, 255, 200, 0); pStroke.color = Color.YELLOW }
            SignalState.NONE -> { pFill.color = Color.argb(25, 255, 255, 255); pStroke.color = Color.WHITE }
        }
        canvas.drawRect(r, pFill); canvas.drawRect(r, pStroke)
        canvas.drawLine(width / 2f, r.top - 20, width / 2f, r.bottom + 20, pStroke)
    }
}

class CalibView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var bmp: Bitmap? = null
    var left = 0f
    var right = 0f
    var lineY = 0f // ИСПРАВЛЕНО: переименовано из 'y' в 'lineY', чтобы не конфликтовать с View.getY()

    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GREEN; strokeWidth = 5f }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = bmp ?: return
        val sc = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        val dx = (width - b.width * sc) / 2
        val dy = (height - b.height * sc) / 2

        canvas.drawBitmap(b, null, RectF(dx, dy, dx + b.width * sc, dy + b.height * sc), null)
        canvas.drawLine(dx + left * sc, dy, dx + left * sc, dy + b.height * sc, pLine)
        canvas.drawLine(dx + right * sc, dy, dx + right * sc, dy + b.height * sc, pLine)
        canvas.drawLine(dx, dy + lineY * sc, dx + b.width * sc, dy + lineY * sc, pLine) // ИСПРАВЛЕНО
    }
}