package com.khanproxy

object ProtoHelper {
    private fun readVarint(data: ByteArray, pos: Int): Pair<Long, Int> {
        var result = 0L; var shift = 0; var p = pos
        while (p < data.size) {
            val b = data[p].toInt() and 0xFF; p++
            result = result or ((b.toLong() and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 63) break
        }
        return Pair(result, p)
    }

    fun extract(raw: ByteArray, vararg targets: Int): Map<Int, String> {
        val found = HashMap<Int, String>()
        var pos = 0
        try {
            while (pos < raw.size && found.size < targets.size) {
                val (tagVal, newPos) = readVarint(raw, pos); pos = newPos
                val fieldNum = (tagVal shr 3).toInt()
                val wireType = (tagVal and 0x07).toInt()

                when (wireType) {
                    0 -> { pos = readVarint(raw, pos).second }
                    1 -> pos += 8
                    2 -> {
                        val (len, p2) = readVarint(raw, pos); pos = p2
                        val l = len.toInt()
                        if (l < 0 || pos + l > raw.size) break
                        if (fieldNum in targets) {
                            found[fieldNum] = String(raw, pos, l, Charsets.UTF_8).trim()
                        }
                        pos += l
                    }
                    5 -> pos += 4
                    else -> break
                }
            }
        } catch (_: Exception) {}
        return found
    }
}
