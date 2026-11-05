package com.example.utport

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 스크린샷 레이아웃을 그대로 옮긴 가상패드.
 * 좌표는 원본(2340x1080) 기준 비율이며, 크기는 뷰 높이 기준이다.
 * 멀티터치 지원: 손가락마다 눌린 버튼을 추적해 KeyEvent down/up 으로 변환한다.
 */
class VirtualPadView(context: Context) : View(context) {

    fun interface KeyListener { fun onKey(keyCode: Int, down: Boolean) }

    var listener: KeyListener? = null

    private enum class Shape { CIRCLE, DICE }
    private enum class Icon { NONE, LEFT, RIGHT, UP, DOWN, DICE }

    private class Btn(
        val keyCode: Int,
        val cx: Float, val cy: Float,   // 0..1 (가로/세로 비율)
        val r: Float,                    // 뷰 높이에 대한 반지름 비율
        val shape: Shape,
        val label: String = "",
        val icon: Icon = Icon.NONE,
        val teal: Boolean = false,
    )

    private val W = 2340f
    private val H = 1080f

    private val buttons = listOf(
        // 좌상단: 주사위 2개 + R
        Btn(KeyEvent.KEYCODE_ESCAPE, 565 / W, 92 / H, 64 / H, Shape.DICE, icon = Icon.DICE),
        Btn(KeyEvent.KEYCODE_R, 568 / W, 275 / H, 75 / H, Shape.CIRCLE, label = "R"),
        Btn(KeyEvent.KEYCODE_F4, 723 / W, 275 / H, 64 / H, Shape.DICE, icon = Icon.DICE, teal = true),
        // 좌하단: 좌/우
        Btn(KeyEvent.KEYCODE_DPAD_LEFT, 380 / W, 885 / H, 112 / H, Shape.CIRCLE, icon = Icon.LEFT),
        Btn(KeyEvent.KEYCODE_DPAD_RIGHT, 610 / W, 885 / H, 112 / H, Shape.CIRCLE, icon = Icon.RIGHT),
        // 우상단: C / Z / X
        Btn(KeyEvent.KEYCODE_C, 2013 / W, 170 / H, 112 / H, Shape.CIRCLE, label = "C"),
        Btn(KeyEvent.KEYCODE_Z, 1878 / W, 380 / H, 112 / H, Shape.CIRCLE, label = "Z"),
        Btn(KeyEvent.KEYCODE_X, 2140 / W, 380 / H, 112 / H, Shape.CIRCLE, label = "X"),
        // 우하단: 위/아래
        Btn(KeyEvent.KEYCODE_DPAD_UP, 2030 / W, 630 / H, 112 / H, Shape.CIRCLE, icon = Icon.UP),
        Btn(KeyEvent.KEYCODE_DPAD_DOWN, 2030 / W, 885 / H, 112 / H, Shape.CIRCLE, icon = Icon.DOWN),
    )

    private val faceColor = Color.parseColor("#343733")
    private val sideColor = Color.parseColor("#1E201D")
    private val glyphColor = Color.parseColor("#1A1C19")
    private val tealFace = Color.parseColor("#0B4556")
    private val tealSide = Color.parseColor("#082C38")

    private val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val stroke = Paint().apply {
        isAntiAlias = false; style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER; color = glyphColor
    }
    private val text = Paint().apply {
        isAntiAlias = false; color = glyphColor; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pressed = HashSet<Int>()
    private val rect = RectF()
    private val path = Path()

    var opacity = 0.9f
        set(v) { field = v; invalidate() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        for (b in buttons) {
            val x = b.cx * w; val y = b.cy * h; val r = b.r * h
            val down = b.keyCode in pressed
            val dy = if (down) r * 0.08f else 0f
            val face = if (b.teal) tealFace else faceColor
            val side = if (b.teal) tealSide else sideColor
            val a = (opacity * 255).toInt()

            // 아래쪽 두께(그림자)
            fill.color = side; fill.alpha = a
            drawBody(c, x, y + r * 0.14f - dy, r, b.shape)
            fill.color = if (down) lighten(face) else face; fill.alpha = a
            drawBody(c, x, y + dy * 0.0f, r, b.shape)

            stroke.strokeWidth = r * 0.14f; stroke.alpha = a
            text.alpha = a; text.textSize = r * 1.25f
            val cy = y + dy
            when (b.icon) {
                Icon.LEFT -> arrow(c, x, cy, r, -1f, 0f)
                Icon.RIGHT -> arrow(c, x, cy, r, 1f, 0f)
                Icon.UP -> arrow(c, x, cy, r, 0f, -1f)
                Icon.DOWN -> arrow(c, x, cy, r, 0f, 1f)
                Icon.DICE -> dots(c, x, cy, r)
                Icon.NONE -> c.drawText(b.label, x, cy + r * 0.42f, text)
            }
        }
    }

    private fun lighten(c: Int) = Color.rgb(
        minOf(255, Color.red(c) + 28), minOf(255, Color.green(c) + 28), minOf(255, Color.blue(c) + 28)
    )

    private fun drawBody(c: Canvas, x: Float, y: Float, r: Float, s: Shape) {
        if (s == Shape.CIRCLE) c.drawCircle(x, y, r, fill)
        else { rect.set(x - r, y - r, x + r, y + r); c.drawRoundRect(rect, r * 0.12f, r * 0.12f, fill) }
    }

    private fun arrow(c: Canvas, x: Float, y: Float, r: Float, dx: Float, dy: Float) {
        val len = r * 0.62f; val head = r * 0.34f
        val tx = x + dx * len; val ty = y + dy * len        // 화살촉 끝
        val bx = x - dx * len; val by = y - dy * len
        c.drawLine(bx, by, tx, ty, stroke)
        val px = -dy; val py = dx                            // 수직 방향
        path.reset()
        path.moveTo(tx - dx * head + px * head, ty - dy * head + py * head)
        path.lineTo(tx, ty)
        path.lineTo(tx - dx * head - px * head, ty - dy * head - py * head)
        c.drawPath(path, stroke)
    }

    private fun dots(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = glyphColor; fill.alpha = (opacity * 255).toInt()
        val d = r * 0.5f; val dr = r * 0.17f
        c.drawCircle(x, y - d, dr, fill); c.drawCircle(x, y + d, dr, fill)
        c.drawCircle(x - d, y, dr, fill); c.drawCircle(x + d, y, dr, fill)
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
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
            if (d <= b.r * height * 1.25f && d < bestD) { best = b; bestD = d }
        }
        return best?.keyCode
    }

    /** 터치 영역이 없는 곳은 게임 화면으로 통과시키지 않고 무시한다. */
    override fun performClick(): Boolean = super.performClick()
}
