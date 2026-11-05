package com.example.utport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 팬게임(GameMaker 윈도우 빌드)을 모바일용 구조로 변환해 앱 내부 저장소에 넣는다.
 *
 *  games/<이름>/assets/game.droid   <- data.win (이름만 변경)
 *  games/<이름>/assets/ (ogg 등)    <- 같은 폴더의 사운드/외부 파일 (소문자, 평탄화)
 *
 * .exe/.dll 은 모바일에서 쓰지 않으므로 제외한다.
 */
object GameImporter {

    private val SKIP_EXT = setOf("exe", "dll", "bat", "lnk")

    fun gamesDir(ctx: Context) = File(ctx.filesDir, "games").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        gamesDir(ctx).listFiles { f -> File(f, "assets/game.droid").exists() }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()

    /** 폴더(SAF) 통째로 가져오기 */
    fun importTree(ctx: Context, tree: Uri): Result<File> = runCatching {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: error("폴더를 열 수 없습니다")
        val out = freshDir(ctx, root.name ?: "game")
        val assets = File(out, "assets").apply { mkdirs() }
        copyTree(ctx, root, assets)
        finish(out)
    }

    /** zip 또는 data.win 단일 파일 가져오기 */
    fun importFile(ctx: Context, uri: Uri): Result<File> = runCatching {
        val name = displayName(ctx, uri) ?: "game"
        val base = name.substringBeforeLast('.')
        val out = freshDir(ctx, base)
        val assets = File(out, "assets").apply { mkdirs() }
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            if (name.endsWith(".zip", true)) unzip(ins, assets)
            else copyStream(ins, File(assets, mapName(name)))
        }
        finish(out)
    }

    fun delete(game: File) { game.deleteRecursively() }

    // ---- 내부 구현 ----

    private fun finish(out: File): File {
        val assets = File(out, "assets")
        if (!File(assets, "game.droid").exists()) {
            out.deleteRecursively()
            error("data.win 을 찾지 못했습니다. 게임 폴더(또는 zip)를 선택하세요.")
        }
        return out
    }

    /** data.win -> game.droid, 나머지는 소문자 파일명 */
    private fun mapName(raw: String): String {
        val n = raw.substringAfterLast('/').lowercase()
        return if (n == "data.win" || n == "game.win") "game.droid" else n
    }

    private fun skip(name: String) = name.substringAfterLast('.', "").lowercase() in SKIP_EXT

    private fun copyTree(ctx: Context, dir: DocumentFile, dest: File) {
        for (f in dir.listFiles()) {
            val n = f.name ?: continue
            if (f.isDirectory) { copyTree(ctx, f, dest) ; continue }   // 하위 폴더도 평탄화
            if (skip(n)) continue
            ctx.contentResolver.openInputStream(f.uri)?.use { copyStream(it, File(dest, mapName(n))) }
        }
    }

    private fun unzip(ins: InputStream, dest: File) {
        ZipInputStream(ins).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory && !skip(e.name)) {
                    // zip-slip 방지: 파일명만 사용하고 경로는 버린다
                    val out = File(dest, mapName(e.name))
                    if (out.canonicalPath.startsWith(dest.canonicalPath)) copyStream(z, out)
                }
                z.closeEntry(); e = z.nextEntry
            }
        }
    }

    private fun copyStream(ins: InputStream, out: File) {
        out.outputStream().use { ins.copyTo(it, 64 * 1024) }
    }

    private fun freshDir(ctx: Context, rawName: String): File {
        val safe = rawName.replace(Regex("[^A-Za-z0-9가-힣._ -]"), "_").ifBlank { "game" }
        var d = File(gamesDir(ctx), safe); var i = 2
        while (d.exists()) { d = File(gamesDir(ctx), "$safe ($i)"); i++ }
        d.mkdirs(); return d
    }

    private fun displayName(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
}
