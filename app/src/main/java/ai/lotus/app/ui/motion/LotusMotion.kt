package ai.lotus.app.ui.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.sqrt

/**
 * 全 App 统一的动效 token。
 *
 * 参数直接沿用 morphicons 的 App 端预设，而不是随手改 stiffness：
 * - smooth: k=170, c=26，临界阻尼，不越过目标；
 * - snappy: k=420, c=30，默认交互，快且只有轻微回弹；
 * - bouncy: k=300, c=14，只用于确实需要俏皮感的图标状态切换。
 *
 * Compose 的 spring 接收 dampingRatio，而 morphicons 暴露的是 damping；
 * 因此这里用 ζ = c / (2·√k) 精确换算，避免又出现每个页面一套手感。
 */
enum class LotusSpringPreset(val stiffness: Float, val damping: Float) {
  Smooth(170f, 26f),
  Snappy(420f, 30f),
  Bouncy(300f, 14f),
}

fun <T> lotusSpring(preset: LotusSpringPreset = LotusSpringPreset.Snappy): SpringSpec<T> {
  val dampingRatio = preset.damping / (2f * sqrt(preset.stiffness))
  return spring(dampingRatio = dampingRatio, stiffness = preset.stiffness)
}

/** Press/release 也分两段：按下响应要快，抬手用临界阻尼收得干净。 */
fun <T> lotusPressSpring(): SpringSpec<T> = lotusSpring(LotusSpringPreset.Snappy)
fun <T> lotusReleaseSpring(): SpringSpec<T> = lotusSpring(LotusSpringPreset.Smooth)
