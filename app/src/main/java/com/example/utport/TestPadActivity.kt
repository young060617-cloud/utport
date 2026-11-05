package com.example.utport

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

/**
 * 게임 없이 가상패드만 확인하는 테스트 화면.
 * 눌린 키를 실시간으로 보여주고, 설정(투명도/크기/진동/주사위 키)을 바꿀 수 있다.
 */
class TestPadActivity : Activity() {

    private lateinit var pad: VirtualPadView
    private lateinit var nowText: TextView
    private lateinit var logText: TextView
    private val down = LinkedHashSet<String>()
    private val log = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        center.addView(TextView(this).apply {
            text = "가상패드 테스트"; textSize = 18f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
        })
        nowText = TextView(this).apply {
            textSize = 40f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; text = "-"
        }
        center.addView(nowText)
        logText = TextView(this).apply {
            textSize = 13f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        center.addView(logText)
        root.addView(center, FrameLayout.LayoutParams(-1, -1))

        pad = VirtualPadView(this).apply {
            listener = VirtualPadView.KeyListener { code, isDown -> onKey(code, isDown) }
        }
        root.addView(pad, FrameLayout.LayoutParams(-1, -1))

        val settings = Button(this).apply {
            text = "설정"
            setOnClickListener { showSettings() }
        }
        root.addView(settings, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        setContentView(root)
    }

    private fun keyName(code: Int) = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    private fun onKey(code: Int, isDown: Boolean) {
        val n = keyName(code)
        if (isDown) down.add(n) else down.remove(n)
        nowText.text = if (down.isEmpty()) "-" else down.joinToString(" + ")
        log.addFirst((if (isDown) "▼ " else "▲ ") + n)
        while (log.size > 5) log.removeLast()
        logText.text = log.joinToString("\n")
    }

    private fun showSettings() {
        val pad8 = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad8, pad8, pad8, pad8) }

        fun label(t: String) = TextView(this).apply { text = t; setTextColor(Color.BLACK) }

        // 투명도 30~100%
        val opLabel = label("투명도 ${(PadSettings.opacity(this) * 100).toInt()}%")
        box.addView(opLabel)
        box.addView(SeekBar(this).apply {
            max = 70; progress = (PadSettings.opacity(this@TestPadActivity) * 100).toInt() - 30
            setOnSeekBarChangeListener(seek { p ->
                val v = (p + 30) / 100f
                PadSettings.setOpacity(this@TestPadActivity, v); opLabel.text = "투명도 ${p + 30}%"; pad.reload()
            })
        })

        // 크기 60~140%
        val scLabel = label("버튼 크기 ${(PadSettings.scale(this) * 100).toInt()}%")
        box.addView(scLabel)
        box.addView(SeekBar(this).apply {
            max = 80; progress = (PadSettings.scale(this@TestPadActivity) * 100).toInt() - 60
            setOnSeekBarChangeListener(seek { p ->
                PadSettings.setScale(this@TestPadActivity, (p + 60) / 100f)
                scLabel.text = "버튼 크기 ${p + 60}%"; pad.reload()
            })
        })

        box.addView(Switch(this).apply {
            text = "진동"; isChecked = PadSettings.haptic(this@TestPadActivity)
            setOnCheckedChangeListener { _, on -> PadSettings.setHaptic(this@TestPadActivity, on); pad.reload() }
        })

        fun diceSpinner(title: String, cur: Int, save: (Int) -> Unit) {
            box.addView(label(title))
            box.addView(Spinner(this).apply {
                adapter = ArrayAdapter(this@TestPadActivity, android.R.layout.simple_spinner_dropdown_item,
                    PadSettings.DICE_CHOICES.map { it.first })
                setSelection(PadSettings.DICE_CHOICES.indexOfFirst { it.second == cur }.coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) {
                        save(PadSettings.DICE_CHOICES[i].second); pad.reload()
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            })
        }
        diceSpinner("주사위(회색) 키", PadSettings.dice1(this)) { PadSettings.setDice1(this, it) }
        diceSpinner("주사위(청록) 키", PadSettings.dice2(this)) { PadSettings.setDice2(this, it) }

        AlertDialog.Builder(this).setTitle("가상패드 설정").setView(box)
            .setNeutralButton("초기화") { _, _ -> PadSettings.reset(this); pad.reload() }
            .setPositiveButton("닫기", null).show()
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
}
