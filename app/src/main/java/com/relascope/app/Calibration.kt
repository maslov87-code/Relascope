package com.relascope.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

object CalibrationStore {
    private const val PREF = "relascope_calib"

    fun save(ctx: Context, k: Float) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putFloat("k", k).putString("model", android.os.Build.MODEL).apply()

    fun k(ctx: Context): Float = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getFloat("k", 0f)

    fun model(ctx: Context): String? = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("model", null)

    // ИСПРАВЛЕНО: Калибровка считается валидной только если сохраненная модель совпадает с текущей
    fun has(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val savedModel = prefs.getString("model", null)
        val currentModel = android.os.Build.MODEL
        val k = prefs.getFloat("k", 0f)
        return k > 0f && savedModel == currentModel
    }
}

object CalibrationEngine {
    class Guess(val left: Int, val right: Int, val y: Int, val frameW: Int, val frameH: Int)

    fun findNotch(orig: Bitmap): Guess? {
        val scale = minOf(1f, 1000f / orig.width)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(orig, (orig.width * scale).toInt(), (orig.height * scale).toInt(), true) else orig
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val mask = BooleanArray(w * h)
        val hsv = FloatArray(3)
        var minRow = h; var maxRow = -1; var minCol = w; var maxCol = -1
        for (y in 0 until h) for (x in 0 until w) {
            Color.colorToHSV(px[y * w + x], hsv)
            val red = (hsv[0] < 20 || hsv[0] > 340) && hsv[1] > 0.5 && hsv[2] > 0.25
            mask[y * w + x] = red
            if (red) { if (y < minRow) minRow = y; if (y > maxRow) maxRow = y; if (x < minCol) minCol = x; if (x > maxCol) maxCol = x }
        }
        if (maxRow - minRow < 50 || maxCol - minCol < 30) return null
        val gaps = mutableListOf<IntArray>()
        for (y in minRow until minRow + (maxRow - minRow) / 2) {
            val runs = mutableListOf<IntArray>()
            var x = minCol
            while (x <= maxCol) {
                if (mask[y * w + x]) { var x2 = x; while (x2 + 1 <= maxCol && mask[y * w + x2 + 1]) x2++; runs.add(intArrayOf(x, x2)); x = x2 + 1 } else x++
            }
            if (runs.size == 2) {
                val gl = runs[0][1] + 1; val gr = runs[1][0] - 1
                val gap = gr - gl + 1; val bw = maxCol - minCol + 1
                if (gap > bw * 0.1 && gap < bw * 0.7) gaps.add(intArrayOf(gl, gr, y))
            }
        }
        if (gaps.isEmpty()) return null
        gaps.sortBy { it[1] - it[0] }
        val m = gaps[gaps.size / 2]
        // ИСПРАВЛЕНО: возвращаем координаты в МАСШТАБЕ ИСХОДНОГО фото, а не уменьшенной копии
        val inv = 1f / scale
        return Guess((m[0] * inv).toInt(), (m[1] * inv).toInt(), (m[2] * inv).toInt(), orig.width, orig.height)
    }
}