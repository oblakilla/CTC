package com.ctc.tagchecker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Маленькая перетаскиваемая панель поверх игры: Старт/Стоп, калибровка, цвета, прогресс. */
class PanelOverlay(private val svc: CheckerService) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root = LinearLayout(svc)
    private val title = TextView(svc)
    private val status = TextView(svc)
    private val progressText = TextView(svc)
    private val bar = ProgressBar(svc, null, android.R.attr.progressBarStyleHorizontal)
    private val btnStart = Button(svc)
    private var added = false

    private val lp = WindowManager.LayoutParams(
        dp(240),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    init {
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(40)

        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(10), dp(8), dp(10), dp(8))
        val bg = GradientDrawable()
        bg.setColor(Color.argb(230, 18, 22, 26))
        bg.cornerRadius = dp(14).toFloat()
        root.background = bg

        title.text = "CTC  ✥ тяни сюда"
        title.setTextColor(Color.WHITE)
        title.textSize = 14f
        title.setPadding(0, 0, 0, dp(4))

        status.setTextColor(Color.rgb(190, 230, 210))
        status.textSize = 12f
        status.text = "Готов"

        progressText.setTextColor(Color.rgb(220, 220, 220))
        progressText.textSize = 12f
        progressText.text = "0 / 0"

        bar.max = 1000
        bar.progress = 0

        root.addView(title)
        root.addView(status)
        root.addView(bar)
        root.addView(progressText)

        val row1 = row()
        btnStart.text = "Старт"
        style(btnStart)
        btnStart.setOnClickListener {
            if (svc.running) svc.stopChecking() else svc.startChecking()
            setRunning(svc.running)
        }
        row1.addView(btnStart, weight())
        row1.addView(btn("Калибр.") { svc.startCalibration() }, weight())
        root.addView(row1)

        val row2 = row()
        row2.addView(btn("✓цвет") { svc.sampleColor(true) }, weight())
        row2.addView(btn("✗цвет") { svc.sampleColor(false) }, weight())
        row2.addView(btn("Скрыть") { svc.hidePanel() }, weight())
        root.addView(row2)

        enableDrag()
    }

    private fun dp(v: Int): Int = (v * svc.resources.displayMetrics.density).toInt()

    private fun row(): LinearLayout {
        val r = LinearLayout(svc)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun style(b: Button) {
        b.textSize = 11f
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        b.isAllCaps = false
        b.setPadding(dp(4), dp(6), dp(4), dp(6))
    }

    private fun btn(text: String, onClick: () -> Unit): Button {
        val b = Button(svc)
        b.text = text
        style(b)
        b.setOnClickListener { onClick() }
        return b
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag() {
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (e.rawX - sx).toInt()
                    lp.y = oy + (e.rawY - sy).toInt()
                    if (added) wm.updateViewLayout(root, lp)
                }
            }
            true
        }
    }

    fun show() {
        if (added) return
        wm.addView(root, lp)
        added = true
    }

    fun hide() {
        if (!added) return
        try { wm.removeView(root) } catch (_: Exception) {}
        added = false
    }

    fun setStatus(text: String) {
        status.text = text
    }

    fun setProgress(done: Int, total: Int, info: String) {
        bar.progress = if (total > 0) (done * 1000L / total).toInt() else 0
        progressText.text = "$done / $total   $info"
    }

    fun setRunning(r: Boolean) {
        btnStart.text = if (r) "Стоп" else "Старт"
    }

    /** Прячем панель на время скриншота, чтобы она не попала в проверку пикселей. */
    fun setInvisible(inv: Boolean) {
        root.alpha = if (inv) 0f else 1f
    }
}
