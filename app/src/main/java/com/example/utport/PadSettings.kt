package com.example.utport

import android.content.Context
import android.view.KeyEvent

/** 가상패드 설정 저장소 (SharedPreferences). */
object PadSettings {

    /** 주사위 버튼에 지정할 수 있는 키 목록 (표시 이름 -> 키코드) */
    val DICE_CHOICES: List<Pair<String, Int>> = listOf(
        "ESC" to KeyEvent.KEYCODE_ESCAPE,
        "F4" to KeyEvent.KEYCODE_F4,
        "ENTER" to KeyEvent.KEYCODE_ENTER,
        "TAB" to KeyEvent.KEYCODE_TAB,
        "SPACE" to KeyEvent.KEYCODE_SPACE,
    )

    private fun p(c: Context) = c.getSharedPreferences("pad", Context.MODE_PRIVATE)

    fun opacity(c: Context) = p(c).getFloat("opacity", 0.9f)
    fun scale(c: Context) = p(c).getFloat("scale", 1.0f)
    fun haptic(c: Context) = p(c).getBoolean("haptic", true)
    fun dice1(c: Context) = p(c).getInt("dice1", KeyEvent.KEYCODE_ESCAPE)
    fun dice2(c: Context) = p(c).getInt("dice2", KeyEvent.KEYCODE_F4)

    fun setOpacity(c: Context, v: Float) = p(c).edit().putFloat("opacity", v).apply()
    fun setScale(c: Context, v: Float) = p(c).edit().putFloat("scale", v).apply()
    fun setHaptic(c: Context, v: Boolean) = p(c).edit().putBoolean("haptic", v).apply()
    fun setDice1(c: Context, v: Int) = p(c).edit().putInt("dice1", v).apply()
    fun setDice2(c: Context, v: Int) = p(c).edit().putInt("dice2", v).apply()

    fun reset(c: Context) = p(c).edit().clear().apply()
}
