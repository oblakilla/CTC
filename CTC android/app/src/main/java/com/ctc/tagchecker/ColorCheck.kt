package com.ctc.tagchecker

import android.graphics.Bitmap
import android.graphics.Color

object ColorCheck {
    const val FREE = 1
    const val TAKEN = 2
    const val UNKNOWN = 0

    /** Средний цвет квадрата 7x7 вокруг точки — устойчивее, чем один пиксель. */
    fun avgColor(bmp: Bitmap, p: Pt): Int {
        val cx = p.x.toInt().coerceIn(0, bmp.width - 1)
        val cy = p.y.toInt().coerceIn(0, bmp.height - 1)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (dy in -3..3) for (dx in -3..3) {
            val x = (cx + dx).coerceIn(0, bmp.width - 1)
            val y = (cy + dy).coerceIn(0, bmp.height - 1)
            val c = bmp.getPixel(x, y)
            r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
        }
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun dist(a: Int, b: Int): Int {
        val dr = Color.red(a) - Color.red(b)
        val dg = Color.green(a) - Color.green(b)
        val db = Color.blue(a) - Color.blue(b)
        return dr * dr + dg * dg + db * db
    }

    /**
     * Если при калибровке сохранены эталонные цвета — берём ближайший.
     * Иначе эвристика: зелёный = свободен, красный = занят.
     */
    fun classify(c: Int, free: Int?, taken: Int?): Int {
        if (free != null && taken != null) {
            val df = dist(c, free)
            val dt = dist(c, taken)
            val limit = 90 * 90 * 3
            if (df < dt && df < limit) return FREE
            if (dt < df && dt < limit) return TAKEN
            return UNKNOWN
        }
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        if (g > 110 && g > r + 35 && g > b + 15) return FREE
        if (r > 120 && r > g + 45 && r > b + 30) return TAKEN
        return UNKNOWN
    }
}
