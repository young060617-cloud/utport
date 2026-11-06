package com.example.utport

import java.io.File
import java.io.RandomAccessFile

/**
 * GameMaker data.win(game.droid)의 GEN8 청크에서 게임 식별 정보를 읽고,
 * 이 앱이 허용하는 게임(QK Dusttrust)인지 판별한다.
 */
object GameIdentity {

    /**
     * 허용할 게임을 가르는 키워드(소문자). 아래 식별 문자열 중 하나라도 포함하면 통과.
     * QK 버전의 내부 이름을 확정하지 못해서 "qk" 로 잡았다. 거절 메시지에 실제 이름이 나오므로
     * 필요하면 여기만 고치면 된다.
     */
    private val ALLOWED = listOf("qk")

    class Info(val fileName: String, val name: String, val displayName: String)

    /** GameMaker 형식이 아니거나 읽을 수 없으면 null */
    fun read(f: File): Info? = runCatching {
        RandomAccessFile(f, "r").use { r ->
            val len = r.length()
            if (len < 256) return@use null

            val head = ByteArray(16); r.readFully(head)
            if (String(head, 0, 4, Charsets.US_ASCII) != "FORM") return@use null
            if (String(head, 8, 4, Charsets.US_ASCII) != "GEN8") return@use null

            val size = le32(head, 12)
            if (size < 104) return@use null
            val data = ByteArray(minOf(size, 4096)); r.readFully(data)

            fun str(p: Long): String {
                if (p <= 4 || p + 1 >= len) return ""
                r.seek(p - 4)
                val b4 = ByteArray(4); r.readFully(b4)
                val n = le32(b4, 0)
                if (n !in 1..512 || p + n > len) return ""
                val buf = ByteArray(n); r.readFully(buf)
                return String(buf, Charsets.UTF_8)
            }
            fun ptr(o: Int) = le32(data, o).toLong() and 0xFFFFFFFFL

            Info(
                fileName = str(ptr(4)),
                name = str(ptr(40)),
                displayName = str(ptr(100)),
            )
        }
    }.getOrNull()

    /** 식별 문자열 또는 가져온 폴더/파일 이름에 허용 키워드가 있으면 true */
    fun isAllowed(info: Info, sourceName: String): Boolean {
        val signals = listOf(info.fileName, info.name, info.displayName, sourceName).map { it.lowercase() }
        return signals.any { s -> ALLOWED.any { s.contains(it) } }
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
