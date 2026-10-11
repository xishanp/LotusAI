package ai.lotus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Lotus AI 配色 —— **纯白 + 零彩色**（定稿方向 docs/UI-SPEC.md）。
 *
 * 设计要点：白底上液态玻璃只能靠**边缘**显现（内部折射仍是白），
 * 所以这里的常量只服务两件事：① 玻璃的唯一存在感来源是描边/柔影；
 * ② 文字与图标用两级灰度拉开层次。**不要往这里加彩色强调色。**
 */
object LotusPalette {
  /**
   * 画布：**苹果风冷白**（近似 iOS 的 systemGroupedBackground），不是纯 `#FFFFFF`。
   *
   * 为什么不给纯白：玻璃"有得糊、有明暗可折"的前提是底下不是一块死白。
   * 纯 `#FFFFFF` 上，模糊 / 折射 / 白高光全部退化 —— 玻璃会彻底隐形
   * （用户原话：「就算是纯白背景苹果也能一眼看出来，我们的却不行」）。
   *
   * ⚠️ 必须画在 `layerBackdrop` 内部，否则玻璃采样到空纹理。
   * ⚠️ 和 `res/values/colors.xml` 的 `lotus_canvas`、以及启动页背景**必须是同一个值**，
   *    否则冷启动那几百毫秒会白一下再跳到这个色。
   */
  val Canvas = Color(0xFFF6F6F9)

  /** 正文/图标主色。 */
  val Ink = Color(0xFF1C1C1E)

  /** 次级文字（副标题、placeholder、未选中图标）。 */
  val InkMuted = Color(0xFF8E8E93)

  /** 更淡的一级，用于极轻的辅助信息。 */
  val InkFaint = Color(0xFFB8B8BE)

  /** 玻璃容器色：几乎全透，只留一点点实体感。 */
  val GlassVehicle = Color(0x14FFFFFF)

  /** 玻璃描边/分隔线。 */
  val Hairline = Color(0xFFE5E5EA)

  /** 选中态的小圆点。 */
  val SelectionDot = Color(0xFF1C1C1E)
}

private val LotusLightScheme =
  lightColorScheme(
    primary = LotusPalette.Ink,
    onPrimary = Color.White,
    background = LotusPalette.Canvas,
    onBackground = LotusPalette.Ink,
    surface = LotusPalette.Canvas,
    onSurface = LotusPalette.Ink,
    surfaceVariant = LotusPalette.Canvas,
    onSurfaceVariant = LotusPalette.InkMuted,
    outline = LotusPalette.Hairline,
  )

@Composable
fun LotusTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = LotusLightScheme, content = content)
}
