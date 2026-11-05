package com.example.utport

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.File

class GameActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val gameDir = File(intent.getStringExtra("dir") ?: run { finish(); return })

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val gameLayer = FrameLayout(this)
        root.addView(gameLayer, FrameLayout.LayoutParams(-1, -1))
        GameRunner.attach(this, gameLayer, gameDir)

        val pad = VirtualPadView(this).apply {
            listener = VirtualPadView.KeyListener { code, down -> sendKey(code, down) }
        }
        root.addView(pad, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    /** 가상패드 입력을 실제 키 이벤트로 주입 (러너는 일반 키보드 입력으로 받는다). */
    private fun sendKey(code: Int, down: Boolean) {
        val t = android.os.SystemClock.uptimeMillis()
        val e = KeyEvent(t, t, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0)
        window.decorView.dispatchKeyEvent(e)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
}
