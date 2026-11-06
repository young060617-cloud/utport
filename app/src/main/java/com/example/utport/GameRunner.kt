package com.example.utport

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File

/**
 * GameMaker 런타임(러너)을 붙이는 지점.
 *
 * 변환된 게임(assets/game.droid)을 실제로 실행하려면 GameMaker Android 러너가 필요하다.
 * 러너는 YoYo Games 의 소유물이라 이 프로젝트에 포함하지 않았다.
 * 본인이 합법적으로 가진 러너(예: 정식 Android 빌드의 네이티브 라이브러리)를
 * 아래 attach() 에서 로드/연결하면 된다. 연결 전에는 안내 화면만 표시한다.
 */
object GameRunner {

    fun isRunnerInstalled(activity: Activity): Boolean =
        File(activity.filesDir, "runner").listFiles()?.isNotEmpty() == true

    /** 러너가 그릴 뷰를 container 에 붙인다. 붙였으면 true. */
    fun attach(activity: Activity, container: FrameLayout, gameDir: File): Boolean {
        if (!isRunnerInstalled(activity)) {
            container.addView(TextView(activity).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                text = "게임 변환 완료: ${gameDir.name}\n" +
                    (GameIdentity.summary(File(gameDir, "assets/game.droid")) ?: "") + "\n\n" +
                    "실행하려면 GameMaker 러너를 연결해야 합니다.\n" +
                    "(files/runner/ 에 러너 라이브러리를 넣고 GameRunner.attach 를 구현)"
                gravity = android.view.Gravity.CENTER
            }, ViewGroup.LayoutParams(-1, -1))
            return false
        }
        // TODO: 러너 로드 -> GLSurfaceView 를 container.addView(...) 로 추가
        //       게임 데이터 경로: File(gameDir, "assets")
        return true
    }
}
