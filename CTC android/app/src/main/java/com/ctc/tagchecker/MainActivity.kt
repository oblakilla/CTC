package com.ctc.tagchecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var rbTwo: RadioButton
    private lateinit var rbTwoDigit: RadioButton
    private lateinit var cbLetters: CheckBox
    private lateinit var cbDigits: CheckBox
    private lateinit var etDelay: EditText
    private lateinit var etStart: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rbTwo = findViewById(R.id.rbTwo)
        rbTwoDigit = findViewById(R.id.rbTwoDigit)
        cbLetters = findViewById(R.id.cbLetters)
        cbDigits = findViewById(R.id.cbDigits)
        etDelay = findViewById(R.id.etDelay)
        etStart = findViewById(R.id.etStart)
        tvStatus = findViewById(R.id.tvStatus)

        val s = Prefs(this)
        rbTwo.isChecked = !s.plusDigit
        rbTwoDigit.isChecked = s.plusDigit
        cbLetters.isChecked = s.letters
        cbDigits.isChecked = s.digits
        etDelay.setText(s.delayMs.toString())
        etStart.setText(s.startFrom)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            save()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnPanel).setOnClickListener {
            save()
            val svc = CheckerService.instance
            if (svc == null) {
                toast("Сначала включите службу CTC Tag Checker в Спец. возможностях")
            } else {
                svc.showPanel()
                toast("Панель показана. Откройте Standoff 2.")
            }
        }
        findViewById<Button>(R.id.btnResults).setOnClickListener { showResults() }
        findViewById<Button>(R.id.btnResetCalib).setOnClickListener {
            Prefs(this).calibration = ""
            CheckerService.instance?.reloadCalibration()
            toast("Калибровка сброшена")
            refreshStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun save() {
        val s = Prefs(this)
        s.plusDigit = rbTwoDigit.isChecked
        s.letters = cbLetters.isChecked
        s.digits = cbDigits.isChecked
        s.delayMs = etDelay.text.toString().toIntOrNull()?.coerceIn(150, 10000) ?: 900
        s.startFrom = etStart.text.toString().trim().uppercase()
    }

    private fun refreshStatus() {
        val on = CheckerService.instance != null
        val calib = Calibration.fromJson(Prefs(this).calibration)
        val total = TagGenerator.generate(cbLetters.isChecked, cbDigits.isChecked, rbTwoDigit.isChecked).size
        val a = if (on) "Служба: ВКЛЮЧЕНА" else "Служба: выключена"
        val b = if (calib.isComplete()) "есть" else "нет"
        tvStatus.text = "$a  |  Калибровка: $b  |  Тегов в переборе: $total"
    }

    private fun showResults() {
        val text = Prefs(this).results.trim()
        val shown = if (text.isEmpty()) "Пока пусто" else text
        AlertDialog.Builder(this)
            .setTitle("Свободные теги")
            .setMessage(shown)
            .setPositiveButton("Копировать") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("tags", text))
                toast("Скопировано")
            }
            .setNeutralButton("Очистить") { _, _ ->
                Prefs(this).results = ""
                toast("Список очищен")
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
