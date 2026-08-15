package com.relascope.app

import androidx.camera.core.ImageProxy

enum class SignalState { NONE, COUNT, NOCOUNT, REPEAT }

interface TrunkDetector {
    fun detect(gray: ByteArray, w: Int, h: Int, cx: Int, cy: Int, notch: Double): SignalState
}

/**
 * Пользователь сам наводит прицел на ствол. Приложение измеряет ширину объекта в центре
 * и сравнивает с прорезью. Три фильтра отсеивают не-стволы (авто, ручки, шкафы):
 * 1) объект высокий (границы видны в >=70% строк высокой зоны);
 * 2) ширина стабильна по высоте;
 * 3) границы непрерывны (без резких прыжков), наклон ствола допускается.
 */
class HeuristicTrunkDetector : TrunkDetector {

    override fun detect(gray: ByteArray, w: Int, h: Int, cx: Int, cy: Int, notch: Double): SignalState {
        if (notch < 4.0) return SignalState.NONE
        val band = (notch * 2.5).toInt()      // высокая зона: ствол продолжается выше/ниже прицела
        val window = (notch * 4.0).toInt()    // окно сканирования в каждую сторону
        val lefts = mutableListOf<Int>()
        val rights = mutableListOf<Int>()
        val widths = mutableListOf<Double>()
        var rows = 0

        var y = cy - band
        while (y <= cy + band) {
            y += 2
            if (y < 2 || y >= h - 2) continue
            rows++
            val cw = maxOf(3, (notch * 0.25).toInt())
            if (cx - cw < 0 || cx + cw >= w) continue
            val center = IntArray(2 * cw + 1) { i -> lum(gray, w, y, cx - cw + i) }
            center.sort()
            val ref = center[center.size / 2]
            val l = findEdge(gray, w, y, cx, -1, window, ref)
            val r = findEdge(gray, w, y, cx, 1, window, ref)
            if (l >= 0 && r >= 0) { lefts.add(l); rights.add(r); widths.add((r - l).toDouble()) }
        }

        // ФИЛЬТР 1: объект "высокий" — границы видны минимум в 70% строк зоны
        if (rows == 0 || widths.size < rows * 7 / 10) return SignalState.NONE
        widths.sort()
        val mw = widths[widths.size / 2]
        if (mw < notch * 0.2) return SignalState.NONE

        // ФИЛЬТР 2: ширина стабильна по высоте (у авто/предметов она скачет)
        var okW = 0
        for (wd in widths) if (wd >= mw * 0.75 && wd <= mw * 1.25) okW++
        if (okW < widths.size * 8 / 10) return SignalState.NONE

        // ФИЛЬТР 3: границы непрерывны, без резких прыжков (наклон ствола разрешён)
        val maxJump = maxOf(3.0, mw * 0.3)
        var jumps = 0
        for (i in 1 until lefts.size) {
            if (Math.abs(lefts[i] - lefts[i - 1]) > maxJump) jumps++
            if (Math.abs(rights[i] - rights[i - 1]) > maxJump) jumps++
        }
        if (lefts.size > 0 && jumps > lefts.size / 5) return SignalState.NONE

        // Сравнение с прорезью
        return when {
            mw > notch * 1.05 -> SignalState.COUNT
            mw < notch * 0.95 -> SignalState.NOCOUNT
            else -> SignalState.REPEAT
        }
    }

    /** Сканирует от центра наружу; возвращает x первой устойчивой контрастной границы или -1. */
    private fun findEdge(gray: ByteArray, w: Int, y: Int, cx: Int, dir: Int, window: Int, ref: Int): Int {
        var persist = 0
        for (i in 0 until window) {
            val xx = cx + dir * (i + 1)
            if (xx < 1 || xx >= w - 1) return -1
            val d = Math.abs(lum(gray, w, y, xx) - ref)
            if (d > 28) {   // порог контраста; если слабо реагирует на тёмные стволы — уменьшите до 20
                persist++
                if (persist >= 3) return xx - dir * 2
            } else {
                persist = 0
            }
        }
        return -1
    }

    /** Яркость с усреднением по 3 строкам для подавления шума. */
    private fun lum(gray: ByteArray, w: Int, y: Int, x: Int): Int {
        val a = gray[(y - 1) * w + x].toInt() and 0xFF
        val b = gray[y * w + x].toInt() and 0xFF
        val c = gray[(y + 1) * w + x].toInt() and 0xFF
        return (a + b + c) / 3
    }
}

object YuvGray {
    class Frame(val data: ByteArray, val w: Int, val h: Int)

    fun from(image: ImageProxy): Frame {
        val p = image.planes[0]
        val buf = p.buffer
        val w = image.width
        val h = image.height
        val src = ByteArray(w * h)
        var i = 0
        for (y in 0 until h) {
            val rs = y * p.rowStride
            for (x in 0 until w) src[i++] = buf.get(rs + x * p.pixelStride)
        }
        return rotate(src, w, h, image.imageInfo.rotationDegrees)
    }

    private fun rotate(src: ByteArray, w: Int, h: Int, deg: Int): Frame {
        if (deg == 0 || deg == 180) return Frame(src, w, h)
        val out = ByteArray(w * h)
        val w2 = h
        for (y in 0 until h) for (x in 0 until w) {
            if (deg == 90) out[x * w2 + (h - 1 - y)] = src[y * w + x]
            else out[(w - 1 - x) * w2 + y] = src[y * w + x]
        }
        return Frame(out, w2, w)
    }
}