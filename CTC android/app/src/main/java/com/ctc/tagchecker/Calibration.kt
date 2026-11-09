package com.ctc.tagchecker

import org.json.JSONObject

data class Pt(val x: Float, val y: Float)

/**
 * Калибровка: где поле ввода, кнопка стирания, кнопка подтверждения (необязательно),
 * точка индикатора результата, цвета «свободен»/«занят» и координаты клавиш.
 */
class Calibration {
    var field: Pt? = null
    var backspace: Pt? = null
    var commit: Pt? = null
    var apply: Pt? = null
    var result: Pt? = null
    var freeColor: Int? = null
    var takenColor: Int? = null
    val keys: MutableMap<Char, Pt> = LinkedHashMap()

    fun isComplete(): Boolean = field != null && backspace != null && result != null && keys.isNotEmpty()

    fun missingKeys(needed: List<Char>): List<Char> = needed.filter { !keys.containsKey(it) }

    fun toJson(): String {
        val o = JSONObject()
        field?.let { o.put("field", pt(it)) }
        backspace?.let { o.put("backspace", pt(it)) }
        commit?.let { o.put("commit", pt(it)) }
        apply?.let { o.put("apply", pt(it)) }
        result?.let { o.put("result", pt(it)) }
        freeColor?.let { o.put("freeColor", it) }
        takenColor?.let { o.put("takenColor", it) }
        val k = JSONObject()
        for ((c, p) in keys) k.put(c.toString(), pt(p))
        o.put("keys", k)
        return o.toString()
    }

    private fun pt(p: Pt): JSONObject = JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble())

    companion object {
        private fun readPt(o: JSONObject, name: String): Pt? {
            val j = o.optJSONObject(name) ?: return null
            return Pt(j.optDouble("x").toFloat(), j.optDouble("y").toFloat())
        }

        fun fromJson(s: String): Calibration {
            val c = Calibration()
            if (s.isBlank()) return c
            try {
                val o = JSONObject(s)
                c.field = readPt(o, "field")
                c.backspace = readPt(o, "backspace")
                c.commit = readPt(o, "commit")
                c.apply = readPt(o, "apply")
                c.result = readPt(o, "result")
                if (o.has("freeColor")) c.freeColor = o.getInt("freeColor")
                if (o.has("takenColor")) c.takenColor = o.getInt("takenColor")
                val k = o.optJSONObject("keys")
                if (k != null) {
                    val it = k.keys()
                    while (it.hasNext()) {
                        val name = it.next()
                        val p = readPt(k, name)
                        if (p != null && name.isNotEmpty()) c.keys[name[0]] = p
                    }
                }
            } catch (e: Exception) {
                return Calibration()
            }
            return c
        }
    }
}
