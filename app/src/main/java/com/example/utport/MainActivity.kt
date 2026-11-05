package com.example.utport

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var list: ListView
    private lateinit var status: TextView
    private var games: List<File> = emptyList()

    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runImport { GameImporter.importTree(this, uri) }
    }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runImport { GameImporter.importFile(this, uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK); setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "UT Port — 팬게임 모바일 포팅"; textSize = 20f; setTextColor(Color.WHITE)
        })
        root.addView(Button(this).apply {
            text = "게임 폴더 가져오기 (data.win 이 있는 폴더)"
            setOnClickListener { pickTree.launch(null) }
        })
        root.addView(Button(this).apply {
            text = "ZIP / data.win 파일 가져오기"
            setOnClickListener { pickFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
        })
        status = TextView(this).apply { setTextColor(Color.LTGRAY) }
        root.addView(status)
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        list.setOnItemClickListener { _, _, i, _ ->
            startActivity(Intent(this, GameActivity::class.java).putExtra("dir", games[i].absolutePath))
        }
        list.setOnItemLongClickListener { _, _, i, _ ->
            android.app.AlertDialog.Builder(this).setMessage("'${games[i].name}' 삭제할까요?")
                .setPositiveButton("삭제") { _, _ -> GameImporter.delete(games[i]); refresh() }
                .setNegativeButton("취소", null).show()
            true
        }
        refresh()
    }

    private fun refresh() {
        games = GameImporter.list(this)
        list.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            games.map { it.name }) {
            override fun getView(p: Int, v: android.view.View?, g: android.view.ViewGroup): android.view.View =
                super.getView(p, v, g).also { (it as TextView).setTextColor(Color.WHITE) }
        }
    }

    private fun runImport(job: () -> Result<File>) {
        status.text = "변환 중…"
        Thread {
            val r = job()
            runOnUiThread {
                status.text = ""
                r.onSuccess { Toast.makeText(this, "가져오기 완료: ${it.name}", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, it.message ?: "실패", Toast.LENGTH_LONG).show() }
                refresh()
            }
        }.start()
    }
}
