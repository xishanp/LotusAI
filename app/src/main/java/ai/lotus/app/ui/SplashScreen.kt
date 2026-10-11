package ai.lotus.app.ui

import ai.lotus.app.R
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

/**
 * 启动页：纯白底 +「立绘 + 应用名」。
 *
 * ## 平台硬限制（v12 实测，别再猜）
 *
 * Android 12+ 的系统启动页由系统自己画，我们能改的只有**两个**东西：背景色（纯色）和图标。
 * 图标的下场是固定的：**画布 288dp、只显示中间那个 192dp 的圆**，其余全部裁掉。
 *
 * 实测方法：把一张"满幅网格 + 红线=画布边(576px) + 蓝圈=半径192px"的图塞进
 * `windowSplashScreenAnimatedIcon`，冷启动截图量像素 —— 蓝圈的包围盒和被裁的网格包围盒
 * **完全重合**（屏幕 504px 直径），红线（画布边）一点都看不见。
 * 结论：系统那一帧里的立绘，最大只能到"内切于 192dp 圆"；`windowSplashScreenBackground`
 * 塞 drawable 同样不生效（只认纯色）。
 *
 * ## 做法：系统帧尽量塞满 + App 层长大到**首页立绘的位置**
 *
 *  1. 系统启动页：`lotus_splash_icon.png` 的立绘已经贴着那个圆（1.09 倍，最远墨点 186px < 192px）；
 *  2. App 第一帧：**同一张图、同样 288dp、同样居中** ⇒ 与系统那帧逐像素重合，看不见接缝；
 *  3. 40ms 后原地长大，并且**终点对齐首页立绘**（用 `homeArt` 里量到的首页立绘布局矩形反推
 *     缩放与位移）—— 立绘墨迹和首页那张**逐像素重合**，所以最后淡出时不是"换了一页"，
 *     而是首页自己显出来；应用名在立绘下方淡入；
 *  4. 整页淡出，首页已经在底下渲染好了。
 *
 * 量不到首页立绘时（极少见）退化为"原地放大 [FallbackGrownScale] 倍、不位移"，绝不会跳错方向。
 *
 * ⚠️ 交接帧的尺寸/位置不许改：一改就会在系统帧 → App 帧之间跳一下。
 *
 * @param alpha 整页透明度：1 → 0 淡出。
 * @param homeArt 首页立绘的**布局矩形**（root 坐标，px）。null = 还没量到。
 */
@Composable
fun LotusSplash(alpha: Float, homeArt: Rect? = null, modifier: Modifier = Modifier) {
  var grown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(GrowDelayMillis)
    grown = true
  }
  val progress by
    animateFloatAsState(
      targetValue = if (grown) 1f else 0f,
      animationSpec = tween(durationMillis = GrowDurationMillis, easing = FastOutSlowInEasing),
      label = "splashGrow",
    )

  // 这张 288dp 画布自己落在哪（root 坐标）。手不写死屏幕中心，交给布局告诉我。
  var canvas by remember { mutableStateOf<Rect?>(null) }
  var nameSize by remember { mutableStateOf(IntSize.Zero) }

  val density = LocalDensity.current
  val canvasPx = with(density) { SplashArtSize.toPx() }
  val nameGapPx = with(density) { NameGap.toPx() }

  val hero = remember(homeArt, canvas, canvasPx) { heroTransform(homeArt, canvas, canvasPx) }
  val scale = 1f + (hero.scale - 1f) * progress
  val tx = hero.tx * progress
  val ty = hero.ty * progress
  val scaledCanvasPx = canvasPx * scale

  val nameAlpha by
    animateFloatAsState(
      targetValue = if (grown) 1f else 0f,
      animationSpec = tween(durationMillis = 320, delayMillis = 140, easing = FastOutSlowInEasing),
      label = "splashName",
    )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .graphicsLayer { this.alpha = alpha }
        .background(LotusPalette.Canvas),
    contentAlignment = Alignment.Center,
  ) {
    // 立绘：系统那一帧就是它，尺寸/位置不许改（288dp、居中）。
    Image(
      painter = painterResource(R.drawable.lotus_splash_icon),
      contentDescription = null,
      modifier =
        Modifier.size(SplashArtSize)
          .onGloballyPositioned { canvas = it.boundsInRoot() }
          .graphicsLayer {
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            scaleX = scale
            scaleY = scale
            translationX = tx
            translationY = ty
          },
    )
    // 应用名：贴在**立绘墨迹**下面（不是画布下面 —— 这张画布的墨迹只占中间 45%）。
    Text(
      text = "Lotus AI",
      color = LotusPalette.Ink,
      fontSize = 25.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 3.sp,
      modifier =
        Modifier.onGloballyPositioned { nameSize = it.size }
          .graphicsLayer {
            val inkBottomFromCenter = (SplashInkBottom - 0.5f) * scaledCanvasPx
            translationX = tx
            translationY = ty + inkBottomFromCenter + nameGapPx + nameSize.height / 2f
            this.alpha = nameAlpha
          },
    )
  }
}

/**
 * 启动页美术图的排版尺寸。系统把它按 **288dp** 铺开、再用一个 **192dp 的圆**裁
 * （实测：可见圆直径 504px @2.625x = 192dp）。所以 Compose 这边也必须按 288dp 画。
 */
internal val SplashArtSize = 288.dp

/** 停多久再长大（把与系统启动页重合的那一帧交给眼睛；越短越像"没换过页"）。 */
private const val GrowDelayMillis = 40L

/** 长大时长。终点是首页立绘的矩形，所以这里只管"走多快"。 */
private const val GrowDurationMillis = 420

/** 量不到首页立绘时的兜底倍率（≈ 首页立绘宽度的档位），只在异常路径用。 */
private const val FallbackGrownScale = 2.22f

/** 应用名离立绘墨迹底边的距离。 */
private val NameGap = 20.dp

// ---- 墨迹占比（都是量出来的，改了图必须同步改）------------------------------------------
//
// 启动图 `lotus_splash_icon.png`（1152px 画布）：墨迹 x 324..831、y 346..862。
private const val SplashInkLeft = 324f / 1152f
private const val SplashInkRight = 831f / 1152f
private const val SplashInkTop = 346f / 1152f
private const val SplashInkBottom = 862f / 1152f
//
// 首页图 `lotus_mascot.png`（1024×1081）：墨迹 x 43..989、y 111..1079。
private const val HomeInkLeft = 43f / 1024f
private const val HomeInkRight = 989f / 1024f
private const val HomeInkTop = 111f / 1081f
private const val HomeInkBottom = 1079f / 1081f

/** 一次"把启动页立绘摆到首页立绘上"的解：倍率 + 位移（px）。 */
private data class HeroTransform(val scale: Float, val tx: Float, val ty: Float)

/**
 * 解出"让两张图的**墨迹**重合"所需的缩放与位移。
 *
 * 两张 PNG 的留白完全不同（启动图墨迹只占画布 44%，首页图占 92%），所以**不能对齐画布**，
 * 必须按墨迹对齐 —— 否则看起来"差不多"，但边缘差出十几 dp。
 */
private fun heroTransform(home: Rect?, canvas: Rect?, canvasPx: Float): HeroTransform {
  if (home == null || canvas == null || canvasPx <= 0f || home.width <= 0f || home.height <= 0f) {
    return HeroTransform(FallbackGrownScale, 0f, 0f)
  }
  val splashInkW = SplashInkRight - SplashInkLeft
  val homeInkW = (HomeInkRight - HomeInkLeft) * home.width
  val scaled = homeInkW / splashInkW // 放大后的画布边长（px）
  val homeInkCx = home.left + (HomeInkLeft + HomeInkRight) * 0.5f * home.width
  val homeInkCy = home.top + (HomeInkTop + HomeInkBottom) * 0.5f * home.height
  val spInkCx = (SplashInkLeft + SplashInkRight) * 0.5f
  val spInkCy = (SplashInkTop + SplashInkBottom) * 0.5f
  // 画布中心应该落在哪：由墨迹中心反推（墨迹中心不等于画布中心）。
  val targetCx = homeInkCx + (0.5f - spInkCx) * scaled
  val targetCy = homeInkCy + (0.5f - spInkCy) * scaled
  return HeroTransform(
    scale = scaled / canvasPx,
    tx = targetCx - canvas.center.x,
    ty = targetCy - canvas.center.y,
  )
}
