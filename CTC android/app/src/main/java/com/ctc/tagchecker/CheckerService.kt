package com.ctc.tagchecker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class CheckerService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CheckerService? = null
        const val TAG = "CTC"
    }

    val main = Handler(Looper.getMainLooper())
    lateinit var prefs: Prefs
    var calib = Calibration()

    private var panel: PanelOverlay? = null
    private var calibOverlay: CalibrationOverlay? = null

    @Volatile
    var running = false
    private var worker: Thread? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        reloadCalibration()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        running = false
        instance = null
        try { panel?.hide() } catch (_: Exception) {}
        try { calibOverlay?.close() } catch (_: Exception) {}
        panel = null
        calibOverlay = null
    }

    fun reloadCalibration() {
        calib = Calibration.fromJson(prefs.calibration)
    }

    // ---------------- Панель ----------------

    fun showPanel() {
        main.post {
            if (panel == null) panel = PanelOverlay(this)
            panel?.show()
            panel?.setRunning(running)
            val needed = TagGenerator.neededChars(prefs.letters, prefs.digits, prefs.plusDigit)
            if (!calib.isComplete() || calib.missingKeys(needed).isNotEmpty()) {
                panel?.setStatus("Нужна калибровка: откройте клавиатуру в игре и нажмите «Калибр.»")
            } else {
                panel?.setStatus("Готов. Старт — начать перебор")
            }
        }
    }

    fun hidePanel() {
        main.post {
            panel?.hide()
            panel = null
        }
    }

    fun status(text: String) {
        Log.i(TAG, text)
        main.post { panel?.setStatus(text) }
    }

    // ---------------- Калибровка ----------------

    fun startCalibration() {
        if (running) {
            status("Сначала нажмите «Стоп»")
            return
        }
        main.post {
            if (calibOverlay != null) return@post
            panel?.hide()
            val needed = TagGenerator.neededChars(prefs.letters, prefs.digits, prefs.plusDigit)
            calibOverlay = CalibrationOverlay(this, needed) { res ->
                calibOverlay = null
                if (res != null) {
                    prefs.calibration = res.toJson()
                    calib = res
                    panel?.show()
                    status("Калибровка сохранена. Совет: нажмите ✓цвет и ✗цвет")
                } else {
                    panel?.show()
                    status("Калибровка отменена")
                }
            }
            calibOverlay?.show()
        }
    }

    /** Запомнить эталонный цвет индикатора (сейчас на экране свободный или занятый тег). */
    fun sampleColor(free: Boolean) {
        val p = calib.result
        if (p == null) {
            status("Сначала сделайте калибровку")
            return
        }
        if (running) return
        Thread({
            val bmp = screenshot()
            if (bmp == null) {
                status("Скриншот не удался (игра может запрещать скриншоты)")
                return@Thread
            }
            val c = ColorCheck.avgColor(bmp, p)
            bmp.recycle()
            if (free) calib.freeColor = c else calib.takenColor = c
            prefs.calibration = calib.toJson()
            val hex = String.format("#%06X", c and 0xFFFFFF)
            status((if (free) "Цвет «свободен» " else "Цвет «занят» ") + "сохранён: " + hex)
        }, "ctc-sample").start()
    }

    // ---------------- Перебор ----------------

    fun startChecking() {
        if (running) return
        reloadCalibration()
        val needed = TagGenerator.neededChars(prefs.letters, prefs.digits, prefs.plusDigit)
        if (!calib.isComplete()) {
            status("Нужна калибровка (кнопка «Калибр.»)")
            return
        }
        val miss = calib.missingKeys(needed)
        if (miss.isNotEmpty()) {
            status("Не откалиброваны клавиши: " + miss.joinToString("") + " — пройдите калибровку заново")
            return
        }
        var tags = TagGenerator.generate(prefs.letters, prefs.digits, prefs.plusDigit)
        val start = prefs.startFrom
        if (start.isNotEmpty()) {
            val idx = tags.indexOf(start)
            if (idx > 0) tags = tags.subList(idx, tags.size)
        }
        val delay = prefs.delayMs.toLong()
        running = true
        worker = Thread({ loop(tags, delay) }, "ctc-worker").also { it.start() }
    }

    fun stopChecking() {
        running = false
    }

    private fun loop(tags: List<String>, delay: Long) {
        var free = 0
        var unknown = 0
        val total = tags.size
        status("Перебор запущен: " + total + " тегов")
        try {
            for ((i, tag) in tags.withIndex()) {
                if (!running) break
                enterTag(tag)
                sleep(delay)
                if (!running) break
                var cls = check()
                if (cls == ColorCheck.UNKNOWN) {
                    sleep(delay / 2 + 350)
                    cls = check()
                }
                when (cls) {
                    ColorCheck.FREE -> {
                        free++
                        prefs.addResult(tag)
                    }
                    ColorCheck.UNKNOWN -> unknown++
                }
                prefs.startFrom = tags.getOrNull(i + 1) ?: ""
                val done = i + 1
                main.post { panel?.setProgress(done, total, tag + "   ✓" + free + "   ?" + unknown) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loop", e)
            status("Ошибка: " + e.message)
        }
        running = false
        status("Стоп. Свободных: " + free + ", неясно: " + unknown)
        main.post { panel?.setRunning(false) }
    }

    private fun enterTag(tag: String) {
        val c = calib
        c.field?.let { tap(it); sleep(150) }
        val bs = c.backspace
        if (bs != null) {
            repeat(5) { tap(bs); sleep(60) }
        }
        for (ch in tag) {
            val p = c.keys[ch] ?: continue
            tap(p)
            sleep(90)
        }
        c.apply?.let { sleep(80); tap(it) }
    }

    private fun check(): Int {
        val p = calib.result ?: return ColorCheck.UNKNOWN
        val bmp = screenshot() ?: return ColorCheck.UNKNOWN
        val c = ColorCheck.avgColor(bmp, p)
        bmp.recycle()
        return ColorCheck.classify(c, calib.freeColor, calib.takenColor)
    }

    // ---------------- Нажатия и скриншоты ----------------

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    private fun tap(p: Pt) {
        val latch = CountDownLatch(1)
        main.post {
            try {
                val path = Path()
                path.moveTo(p.x, p.y)
                val stroke = GestureDescription.StrokeDescription(path, 0, 50)
                val gd = GestureDescription.Builder().addStroke(stroke).build()
                val ok = dispatchGesture(gd, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { latch.countDown() }
                    override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
                }, null)
                if (!ok) latch.countDown()
            } catch (e: Exception) {
                Log.e(TAG, "tap", e)
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
    }

    private fun screenshot(): Bitmap? {
        main.post { panel?.setInvisible(true) }
        sleep(80)
        var out: Bitmap? = null
        for (attempt in 0 until 3) {
            out = screenshotOnce()
            if (out != null) break
            sleep(400)
        }
        main.post { panel?.setInvisible(false) }
        return out
    }

    private fun screenshotOnce(): Bitmap? {
        val latch = CountDownLatch(1)
        var out: Bitmap? = null
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, Executor { it.run() }, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hb = screenshot.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                        out = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        hb.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "shot", e)
                    }
                    latch.countDown()
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "screenshot failed: " + errorCode)
                    latch.countDown()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot", e)
            return null
        }
        latch.await(5, TimeUnit.SECONDS)
        return out
    }
}
