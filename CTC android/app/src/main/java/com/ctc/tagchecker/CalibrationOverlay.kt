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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Полноэкранный полупрозрачный слой: показывает подсказку и запоминает координаты тапа.
 * Перед запуском клавиатура игры должна быть уже открыта.
 */
class CalibrationOverlay(
    private val svc: CheckerService,
    needed: List<Char>,
    private val onDone: (Calibration?) -> Unit
) {
    private data class Step(val id: String, val prompt: String, val optional: Boolean)

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val steps = ArrayList<Step>()
    private var idx = 0
    private val result = Calibration()
    private val root = FrameLayout(svc)
    private val box = LinearLayout(svc)
    private val prompt = TextView(svc)
    private val btnSkip = Button(svc)
    private var atTop = true
    private var added = false
    private var finished = false

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    init {
        steps.add(Step("field", "Тапните по ПОЛЮ ввода клан-тега", false))
        steps.add(Step("backspace", "Тапните по клавише СТЕРЕТЬ (⌫) на клавиатуре", false))
        steps.add(Step("commit", "Тапните по кнопке OK/✓ клавиатуры (подтверждение ввода в поле). Если текст и так попадает в поле — «Пропустить»", true))
        steps.add(Step("result", "Тапните по месту, где появляется ЗЕЛЁНАЯ ГАЛОЧКА / красная надпись «занят»", false))
        steps.add(Step("apply", "Тапните по кнопке проверки/подтверждения. Если её нет — «Пропустить»", true))
        for (ch in needed) {
            steps.add(Step("key_$ch", "Тапните по клавише «$ch»", false))
        }
        buildUi()
        render()
    }

    private fun dp(v: Int): Int = (v * svc.resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        root.setBackgroundColor(Color.argb(55, 0, 0, 0))

        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(10), dp(12), dp(10))
        box.isClickable = true
        val bg = GradientDrawable()
        bg.setColor(Color.argb(235, 18, 22, 26))
        bg.cornerRadius = dp(14).toFloat()
        box.background = bg

        prompt.setTextColor(Color.WHITE)
        prompt.textSize = 15f
        box.addView(prompt)

        val row = LinearLayout(svc)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(btn("Назад") { back() }, weight())
        btnSkip.text = "Пропустить"
        style(btnSkip)
        btnSkip.setOnClickListener { skip() }
        row.addView(btnSkip, weight())
        row.addView(btn("Сдвинуть") { move() }, weight())
        row.addView(btn("Отмена") { finish(false) }, weight())
        box.addView(row)

        val blp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        blp.setMargins(dp(10), dp(24), dp(10), dp(10))
        root.addView(box, blp)

        root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) record(e.rawX, e.rawY)
            true
        }
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

    private fun render() {
        if (idx >= steps.size) return
        val s = steps[idx]
        prompt.text = "Калибровка " + (idx + 1) + "/" + steps.size + "\n" + s.prompt
        btnSkip.isEnabled = s.optional
        btnSkip.alpha = if (s.optional) 1f else 0.4f
    }

    private fun record(x: Float, y: Float) {
        if (idx >= steps.size) return
        val p = Pt(x, y)
        val id = steps[idx].id
        when {
            id == "field" -> result.field = p
            id == "backspace" -> result.backspace = p
            id == "commit" -> result.commit = p
            id == "result" -> result.result = p
            id == "apply" -> result.apply = p
            id.startsWith("key_") && id.length > 4 -> result.keys[id[4]] = p
        }
        next()
    }

    private fun skip() {
        if (idx >= steps.size) return
        val s = steps[idx]
        if (!s.optional) return
        if (s.id == "apply") result.apply = null
        if (s.id == "commit") result.commit = null
        next()
    }

    private fun back() {
        if (idx > 0) idx--
        render()
    }

    private fun move() {
        atTop = !atTop
        val blp = box.layoutParams as FrameLayout.LayoutParams
        blp.gravity = if (atTop) Gravity.TOP else Gravity.CENTER_VERTICAL
        box.layoutParams = blp
    }

    private fun next() {
        idx++
        if (idx >= steps.size) finish(true) else render()
    }

    fun show() {
        if (added) return
        wm.addView(root, lp)
        added = true
    }

    fun close() {
        if (!added) return
        try { wm.removeView(root) } catch (_: Exception) {}
        added = false
    }

    private fun finish(ok: Boolean) {
        if (finished) return
        finished = true
        close()
        onDone(if (ok) result else null)
    }
}
