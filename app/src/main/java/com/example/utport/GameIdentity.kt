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

    class Info(
        val fileName: String, val name: String, val displayName: String,
        val bytecode: Int, val major: Int, val minor: Int, val release: Int, val build: Int,
    )

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
                bytecode = data[1].toInt() and 0xFF,
                major = le32(data, 44), minor = le32(data, 48),
                release = le32(data, 52), build = le32(data, 56),
            )
        }
    }.getOrNull()

    /**
     * 화면 표시용 요약. 데이터 파일 머리글의 값이라 대략적인 버전이다
     * (GMS2 는 2.3 이후 버전도 2.0.0.0 으로 적혀 있는 경우가 많다).
     */
    fun summary(f: File): String? = read(f)?.let {
        val chunks = chunkNames(f)
        val notable = NOTABLE.filter { n -> n in chunks }
        "GameMaker 헤더 ${it.major}.${it.minor}.${it.release}.${it.build} / 바이트코드 ${it.bytecode}\n" +
            "추정 버전: ${minVersion(chunks)} (구성: ${if (notable.isEmpty()) "없음" else notable.joinToString(" ")})"
    }

    // 버전을 가르는 청크 (libgm 의 version_detection 기준)
    private val NOTABLE = listOf("TGIN", "SEQN", "FEDS", "FEAT", "PSEM", "UILR")

    /** 파일 안의 청크 이름 목록. 이름 4글자와 크기 4바이트를 읽고 크기만큼 건너뛴다. */
    fun chunkNames(f: File): List<String> = runCatching {
        RandomAccessFile(f, "r").use { r ->
            val len = r.length()
            val names = ArrayList<String>()
            val h = ByteArray(8)
            var pos = 8L
            while (pos + 8 <= len && names.size < 64) {
                r.seek(pos); r.readFully(h)
                val n = String(h, 0, 4, Charsets.US_ASCII)
                if (!n.all { c -> c in 'A'..'Z' || c in '0'..'9' }) break
                names.add(n)
                pos += 8 + (le32(h, 4).toLong() and 0xFFFFFFFFL)
            }
            names
        }
    }.getOrDefault(emptyList())

    /**
     * 청크가 있으면 그 버전 이상이라는 뜻이다. 새 기능을 안 쓴 게임은 실제보다 낮게 나올 수 있다.
     */
    fun minVersion(chunks: List<String>): String = when {
        "UILR" in chunks -> "2024.13 이상"
        "PSEM" in chunks -> "2023.2 이상"
        "FEAT" in chunks -> "2022.8 이상"
        "FEDS" in chunks -> "2.3.6 이상"
        "SEQN" in chunks -> "2.3.0 이상"
        "TGIN" in chunks -> "2.2.1 이상"
        else -> "2.2.1 미만(2.0~2.2 계열)"
    }

    /** 식별 문자열 또는 가져온 폴더/파일 이름에 허용 키워드가 있으면 true */
    fun isAllowed(info: Info, sourceName: String): Boolean {
        val signals = listOf(info.fileName, info.name, info.displayName, sourceName).map { it.lowercase() }
        return signals.any { s -> ALLOWED.any { s.contains(it) } }
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
