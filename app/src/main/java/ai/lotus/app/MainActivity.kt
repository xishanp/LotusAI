package ai.lotus.app

import ai.lotus.app.ui.LotusApp
import ai.lotus.app.ui.LotusSplash
import ai.lotus.app.data.Prompts
import ai.lotus.app.ui.theme.LotusTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay

/**
 * 启动页停留时长。
 *
 * 屏幕上"看得见启动页"的总时间 = 系统启动页（冷启动窗口，几百毫秒）+ [SplashHoldMillis]
 * + [SplashFadeMillis]。系统那一段本来就必须存在，所以这里刻意压短，别让它变成"等启动页"。
 */
private const val SplashHoldMillis = 660L

/** 启动页淡出时长。 */
private const val SplashFadeMillis = 220

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // 首次启动铺 MD 框架（IDENTITY / SOUL / AGENTS / USER / MEMORY + memory 档案柜）。
    // **已存在的文件一个字节都不动** —— 用户改过就是用户的。放在 setContent 之前，
    // 保证 UI 第一帧读到的就是铺好的内容。
    Prompts.ensureDefaults(this)
    setContent {
      LotusTheme {
        var showSplash by remember { mutableStateOf(true) }
        // 首页立绘的布局矩形（root px）。首页在启动页底下渲染，第一帧就能量到；
        // 启动页用它把"长大后的立绘"摆到首页立绘上 —— 见 ui/SplashScreen.kt 的 heroTransform。
        var homeArt by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(Unit) {
          delay(SplashHoldMillis)
          showSplash = false
        }
        val splashAlpha by
          animateFloatAsState(
            targetValue = if (showSplash) 1f else 0f,
            animationSpec = tween(durationMillis = SplashFadeMillis, easing = LinearEasing),
            label = "splashAlpha",
          )

        Box(Modifier.fillMaxSize()) {
          // 首页照常渲染（在启动页底下），淡出即用完。
          LotusApp(onHomeArtBounds = { homeArt = it })
          // ⚠️ 这里**不做**入场缩放：系统启动页里的立绘已经是 100%，
          // 这里再从 94% 放大的话，交接那一帧会看见"弹一下"。两边必须逐像素一致。
          if (splashAlpha > 0.002f) {
            LotusSplash(alpha = splashAlpha, homeArt = homeArt)
          }
        }
      }
    }
  }
}
