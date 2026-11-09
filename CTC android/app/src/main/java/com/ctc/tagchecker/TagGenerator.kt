package com.ctc.tagchecker

object TagGenerator {
    val LETTERS: List<Char> = ('A'..'Z').toList()
    val DIGITS: List<Char> = ('0'..'9').toList()

    fun baseChars(letters: Boolean, digits: Boolean): List<Char> {
        val out = ArrayList<Char>()
        if (letters) out.addAll(LETTERS)
        if (digits) out.addAll(DIGITS)
        if (out.isEmpty()) out.addAll(LETTERS)
        return out
    }

    /** Все символы, которые нужно набирать (для калибровки клавиш). */
    fun neededChars(letters: Boolean, digits: Boolean, plusDigit: Boolean): List<Char> {
        val set = LinkedHashSet<Char>(baseChars(letters, digits))
        if (plusDigit) set.addAll(DIGITS)
        return set.toList()
    }

    fun generate(letters: Boolean, digits: Boolean, plusDigit: Boolean): List<String> {
        val chars = baseChars(letters, digits)
        val out = ArrayList<String>()
        for (a in chars) for (b in chars) {
            if (plusDigit) {
                for (d in DIGITS) out.add("" + a + b + d)
            } else {
                out.add("" + a + b)
            }
        }
        return out
    }
}
