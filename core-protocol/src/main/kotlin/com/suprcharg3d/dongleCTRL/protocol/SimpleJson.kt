package com.suprcharg3d.dongleCTRL.protocol

/**
 * Tiny zero-dependency JSON parser so core-protocol stays pure Kotlin without reflection or heavy libraries.
 */
internal object SimpleJson {

    fun parseObject(text: String): Map<String, Any?> {
        val parser = Parser(text)
        val value = parser.readValue()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: emptyMap()
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun readValue(): Any? {
            skipWhitespace()
            if (pos >= s.length) return null
            return when (val ch = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> if (ch == '-' || ch in '0'..'9') readNumber() else null
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // '{'
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return map
            }
            while (pos < s.length) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                if (pos < s.length && s[pos] == ':') pos++
                val value = readValue()
                map[key] = value
                skipWhitespace()
                if (pos < s.length && s[pos] == ',') {
                    pos++
                } else if (pos < s.length && s[pos] == '}') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return map
        }

        private fun readArray(): List<Any?> {
            pos++ // '['
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return list
            }
            while (pos < s.length) {
                list.add(readValue())
                skipWhitespace()
                if (pos < s.length && s[pos] == ',') {
                    pos++
                } else if (pos < s.length && s[pos] == ']') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return list
        }

        private fun readString(): String {
            if (pos < s.length && s[pos] == '"') pos++
            val sb = StringBuilder()
            while (pos < s.length) {
                val ch = s[pos++]
                if (ch == '"') break
                if (ch == '\\' && pos < s.length) {
                    when (val esc = s[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= s.length) {
                                val hex = s.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun readNumber(): Number {
            val start = pos
            var isFloat = false
            while (pos < s.length) {
                val ch = s[pos]
                if (ch == '.' || ch == 'e' || ch == 'E') isFloat = true
                if (ch in '0'..'9' || ch == '-' || ch == '+' || ch == '.' || ch == 'e' || ch == 'E') {
                    pos++
                } else {
                    break
                }
            }
            val raw = s.substring(start, pos)
            return if (isFloat) raw.toDoubleOrNull() ?: 0.0 else raw.toLongOrNull() ?: 0L
        }

        private fun readBoolean(): Boolean {
            return if (s.startsWith("true", pos)) {
                pos += 4
                true
            } else {
                pos += 5
                false
            }
        }

        private fun readNull(): Any? {
            pos += 4
            return null
        }

        private fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }
    }
}
