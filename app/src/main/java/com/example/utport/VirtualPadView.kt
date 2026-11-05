package com.example.utport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 스프라이트(도트 이미지)로 그리는 가상패드.
 * 좌표는 원본 스크린샷(2340x1080) 기준 비율이며, 크기는 뷰 높이 기준이다.
 * 눌린 버튼은 노란색 "_p" 스프라이트로 바뀐다. 도트가 깨지지 않도록 정수 배율로만 확대한다.
 * 멀티터치 지원: 손가락마다 눌린 버튼을 추적해 KeyEvent down/up 으로 변환한다.
 */
class VirtualPadView(context: Context) : View(context) {

    fun interface KeyListener { fun onKey(keyCode: Int, down: Boolean) }

    var listener: KeyListener? = null

    private class Btn(
        val keyCode: Int,
        val cx: Float, val cy: Float,   // 0..1 (가로/세로 비율)
        val r: Float,                    // 뷰 높이에 대한 반지름 비율
        val sprite: String,              // drawable 이름 (눌림 버전은 이름 + "_p")
        val hasPressed: Boolean = true,
        val teal: Boolean = false,
    )

    private val W = 2340f
    private val H = 1080f

    private fun buildButtons(): List<Btn> = listOf(
        // 좌상단: 주사위 2개 + R (주사위 키는 설정에서 변경 가능)
        Btn(PadSettings.dice1(context), 565 / W, 92 / H, 64 / H, "pad_dice", hasPressed = false),
        Btn(KeyEvent.KEYCODE_R, 568 / W, 275 / H, 75 / H, "pad_r"),
        Btn(PadSettings.dice2(context), 723 / W, 275 / H, 64 / H, "pad_dice", hasPressed = false, teal = true),
        // 좌하단: 좌/우
        Btn(KeyEvent.KEYCODE_DPAD_LEFT, 380 / W, 885 / H, 112 / H, "pad_left"),
        Btn(KeyEvent.KEYCODE_DPAD_RIGHT, 610 / W, 885 / H, 112 / H, "pad_right"),
        // 우상단: C / Z / X
        Btn(KeyEvent.KEYCODE_C, 2013 / W, 170 / H, 112 / H, "pad_c"),
        Btn(KeyEvent.KEYCODE_Z, 1878 / W, 380 / H, 112 / H, "pad_z"),
        Btn(KeyEvent.KEYCODE_X, 2140 / W, 380 / H, 112 / H, "pad_x"),
        // 우하단: 위/아래
        Btn(KeyEvent.KEYCODE_DPAD_UP, 2030 / W, 630 / H, 112 / H, "pad_up"),
        Btn(KeyEvent.KEYCODE_DPAD_DOWN, 2030 / W, 885 / H, 112 / H, "pad_down"),
    )

    private var buttons: List<Btn> = buildButtons()
    private var scale = 1f
    private var haptic = true

    var opacity = 0.9f
        set(v) { field = v; invalidate() }

    // 도트 그대로 확대(보간 끔)
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dst = RectF()
    private val cache = HashMap<String, Bitmap>()

    // 청록 주사위 (회색 스프라이트를 색 변환)
    private val tealFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        0.21f, 0f, 0f, 0f, 0f,
        0f, 1.25f, 0f, 0f, 0f,
        0f, 0f, 1.65f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)))
    // 눌린 주사위(눌림 스프라이트가 없는 버튼) 밝게
    private val litFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        1.4f, 0f, 0f, 0f, 0f,
        0f, 1.4f, 0f, 0f, 0f,
        0f, 0f, 1.4f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)))
    private val tealLitFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        0.3f, 0f, 0f, 0f, 0f,
        0f, 1.75f, 0f, 0f, 0f,
        0f, 0f, 2.3f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)))

    private val pressed = HashSet<Int>()

    /** 저장된 설정(투명도/크기/진동/주사위 키)을 다시 읽어 반영한다. */
    fun reload() {
        opacity = PadSettings.opacity(context)
        scale = PadSettings.scale(context)
        haptic = PadSettings.haptic(context)
        buttons = buildButtons()
        invalidate()
    }

    init { reload() }

    private fun bmp(name: String): Bitmap = cache.getOrPut(name) {
        val id = resources.getIdentifier(name, "drawable", context.packageName)
        val o = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(resources, id, o)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.alpha = (opacity * 255).toInt()
        for (b in buttons) {
            val down = b.keyCode in pressed
            val bm = if (down && b.hasPressed) bmp(b.sprite + "_p") else bmp(b.sprite)

            // 원 지름(2r)에 가장 가까운 정수 배율 -> 도트가 항상 같은 크기로 보임
            val r = b.r * h * scale
            val k = max(1, (2f * r / bm.width).roundToInt())
            val dw = (k * bm.width).toFloat(); val dh = (k * bm.height).toFloat()
            val left = (b.cx * w - dw / 2f).roundToInt().toFloat()
            val top = (b.cy * h - r).roundToInt().toFloat()
            dst.set(left, top, left + dw, top + dh)

            paint.colorFilter = when {
                b.teal && down -> tealLitFilter
                b.teal -> tealFilter
                down && !b.hasPressed -> litFilter
                else -> null
            }
            c.drawBitmap(bm, null, dst, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = HashSet<Int>()
        val liftIdx = if (e.actionMasked == MotionEvent.ACTION_UP ||
            e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        if (e.actionMasked != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until e.pointerCount) {
                if (i == liftIdx) continue
                hit(e.getX(i), e.getY(i))?.let { now.add(it) }
            }
        }
        for (k in pressed.toList()) if (k !in now) listener?.onKey(k, false)
        for (k in now) if (k !in pressed) {
            listener?.onKey(k, true)
            if (haptic) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
        pressed.clear(); pressed.addAll(now)
        invalidate()
        return true
    }

    /** 터치 위치에서 가장 가까운 버튼(반지름 1.25배까지 허용). */
    private fun hit(px: Float, py: Float): Int? {
        var best: Btn? = null; var bestD = Float.MAX_VALUE
        for (b in buttons) {
            val d = hypot(px - b.cx * width, py - b.cy * height)
            if (d <= b.r * height * scale * 1.25f && d < bestD) { best = b; bestD = d }
        }
        return best?.keyCode
    }

    override fun performClick(): Boolean = super.performClick()
}
