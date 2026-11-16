package ai.lotus.app.ui.nav

import ai.lotus.app.ui.glass.DampedDragAnimation
import ai.lotus.app.ui.glass.glassRim
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Lotus AI 底部导航。
 *
 * ## 当前材质（2026-11-16）
 *
 * - 外栏、折射源层和选中泡全部是**零模糊**的全通透玻璃。
 * - 外栏：页面 backdrop + `lens(24dp, 24dp)` + 边缘高光 + 轻阴影。
 * - 折射源层：把外栏玻璃和图标录进 `tabsBackdrop`，供选中泡采样。
 * - 选中泡：静态边缘折射，按压时增强 lens / scale / 高光；不铺灰色表面。
 * - 不使用 blur、vibrancy、霜面或实色 containerColor。
 *
 * ## 结构约束
 *
 * 1. 玻璃组件必须是录制 backdrop 的兄弟节点，不能自采样。
 * 2. 选中泡采样 `combined(页面, ②)`，保证边缘外的 lens 也有像素可采。
 * 3. `innerShadow` 恒 0；不要用内部灰块伪造玻璃厚度。
 * 4. 拉丝统一取 `abs(velocity)`，避免拖动时上下抖动。
 *
 * @param backdrop 页面背景（用 [ai.lotus.app.ui.glass.GlassBackground] 提供），
 *   必须用 `Modifier.layerBackdrop(backdrop)` 挂在内容树上。
 */
@Composable
fun LotusBottomBar(
  activeKey: String,
  onSelect: (String) -> Unit,
  backdrop: Backdrop,
  modifier: Modifier = Modifier,
) {
  val tabs = LotusTab.entries
  val tabsCount = tabs.size
  val activeIndex = tabs.indexOfFirst { it.key == activeKey }.coerceAtLeast(0)

  // 第二层专用：录 tab 内容给玻璃泡折射用。
  val tabsBackdrop = rememberLayerBackdrop()
  // 外栏自己画出来的那层玻璃（见下面 export 的注释）。玻璃泡拿它当"底座" ——
  // 泡里的背景因此**和整条外栏一样是糊的**，不再是一块比周围更清楚的白色板子。
  val barBackdrop = rememberLayerBackdrop()

  BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
    val density = LocalDensity.current
    val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount.toFloat() }

    // 整条外栏的横向滑移。拖拽时**延迟跟随**（spring 而不是 snapTo）——
    // 泡走在前面、载体慢半拍跟上来，这才是"联动"，而不是整块焊死在一起。
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density, tabWidth) {
      derivedStateOf {
        val fraction = (offsetAnimation.value / tabWidth).fastCoerceIn(-1f, 1f)
        with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
      }
    }

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    var currentIndex by remember { mutableIntStateOf(activeIndex) }

    val dampedDragAnimation =
      remember(animationScope) {
        DampedDragAnimation(
          animationScope = animationScope,
          initialValue = currentIndex.toFloat(),
          valueRange = 0f..(tabsCount - 1).toFloat(),
          visibilityThreshold = 0.001f,
          initialScale = 1f,
          pressedScale = (tabWidth / (48f * density.density)).coerceIn(1.15f, 1.45f),
          onDragStarted = {},
          onDragStopped = {
            val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
            currentIndex = targetIndex
            animateToValue(targetIndex.toFloat())
            // ⚡ ζ = 1（临界阻尼）：归位直接停住，不再来回余震。
            // 用户口径「球放大之后要让它稳定运行，不要让它一直抖来抖去」——
            // 以前这里 ζ=0.65 是欠阻尼，松手后载体要晃两三下，读起来就是"抖"。
            animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 500f, 0.5f)) }
          },
          onDrag = { _, dragAmount ->
            updateValue(
              (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                .fastCoerceIn(0f, (tabsCount - 1).toFloat())
            )
            animationScope.launch {
              offsetAnimation.animateTo(
                offsetAnimation.value + dragAmount.x,
                // ⚡ 同样改成临界阻尼：拖动过程中载体不再振荡。
                spring(1f, 700f, 0.5f),
              )
            }
          },
        )
      }

    LaunchedEffect(activeIndex) {
      snapshotFlow { activeIndex }.collectLatest { index -> currentIndex = index }
    }
    LaunchedEffect(dampedDragAnimation) {
      snapshotFlow { currentIndex }
        .drop(1)
        .collectLatest { index ->
          dampedDragAnimation.animateToValue(index.toFloat())
          onSelect(tabs[index].key)
        }
    }

    val barShape = RoundedCornerShape(percent = 50)

    // ---- ① 外栏（可见）----
    Row(
      Modifier
        .graphicsLayer { translationX = panelOffset }
        // 纯白底上玻璃只能靠"影 + 边"存在，这两样跟 drawBackdrop 一样重要。
        .shadow(
          elevation = 10.dp,
          shape = barShape,
          clip = false,
          ambientColor = Color(0x14000000),
          spotColor = Color(0x24000000),
        )
        .drawBackdrop(
          backdrop = backdrop,
          shape = { Capsule() },
          // 把外栏**画出来的那层玻璃**导出去。玻璃泡必须采样它，不能采样原始页面背景 ——
          // 采样页面 = 泡内是"没糊过的底"，看上去反而比周围清楚，用户原话
          // 「菜单栏底部被选中的地方没有实时的模糊」。
          exportedBackdrop = barBackdrop,
          effects = {
            lens(24f.dp.toPx(), 24f.dp.toPx())
          },
          // 镜面高光：上游默认 0.5dp 宽。和 glassPanel 一样加宽到 1dp，白底上那刀白才立得住。
          highlight = { Highlight.Default.copy(width = 1.dp, alpha = 0.90f) },
          shadow = { Shadow(alpha = 0f) },
          innerShadow = { InnerShadow(radius = 0f.dp, alpha = 0f) },
          layerBlock = {
            val progress = dampedDragAnimation.pressProgress
            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
            scaleX = scale
            scaleY = scale
          },
          onDrawSurface = {},
        )
        .height(BarHeight)
        .fillMaxWidth()
        // ⚡ spectrum = 0：② 是每帧都在录制的折射源，glassRim 默认还会画一圈
        // `sweepGradient` 光谱描边 —— 那层在 ② 上既不参与观感（会被 lens 折掉），
        // 又白烧一份渐变 + 描边。上下两边要的只是"有一条能被折进来的边"，
        // 所以这里只留纯色描边。用户口径「这次怎么有点卡卡的感觉」。
        .glassRim(shape = barShape, alpha = 0.85f, spectrum = 0f)
        .padding(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LotusTabIcons(tabs = tabs, onSelect = onSelect)
    }

    // ---- ② 折射源层（alpha 0，只录进 tabsBackdrop）----
    //
    // ## 这一层是上游的正确写法（Kyant0/AndroidLiquidGlass@kmp · LiquidBottomTabs.kt）
    //
    // ⚠️⚠️ **它不只是图标**：它把外栏玻璃的折射面与图标一起录进 [tabsBackdrop]。
    //
    // 由此，泡（③）折到的就是**已经糊好的底**，而图标是纹理里**本来就清晰**的像素 ——
    // 这两件事**互不干扰**，所以：
    //   · 泡里有模糊（用户口径：「背景是有模糊了」）—— 模糊来自这一层，不是来自泡；
    //   · 图标永远清晰（用户口径：「把底栏的图标给覆盖掉了，像在一块玻璃后面」）—— 不糊它；
    //   · 不需要在泡那一层加 blur（加了就糊图标，历史 bug）。
    //
    // ⚠️ `.alpha(0f)` 必须写在 `.layerBackdrop()` **之前**：图层照录完整内容，只是眼睛看不见。
    // ⚠️ `ColorFilter.tint` 写在**最后**（链尾 = 最内层）：只染图标，不染那层玻璃 ——
    //    上游就是这么写的（它染 accent 蓝，我们这版是单色，用 Ink）。
    Row(
      Modifier
        .clearAndSetSemantics {}
        .alpha(0f)
        .layerBackdrop(tabsBackdrop)
        .graphicsLayer { translationX = panelOffset }
        .drawBackdrop(
          backdrop = backdrop,
          shape = { Capsule() },
          effects = {
            val progress = dampedDragAnimation.pressProgress
            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
          },
          highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
          shadow = { Shadow(alpha = 0f) },
          innerShadow = { InnerShadow(radius = 0f.dp, alpha = 0f) },
          onDrawSurface = {},
        )
        // 🔬 实验 E8：把上下两边做成和左右两边一样 —— **关键是把边线推进球里**。
        //
        // 静止时球高 50dp，按下放大到 ~72.5dp。② 的高度决定上下两条边线落在球内哪个位置：
        //   · ② = 60dp（E1 试过的写法）⇒ 边线落在离球顶/球底 6.25dp（8.6%）处，
        //     正好压在 lens 折射最强的那一圈里 ⇒ 被扯得看不出来；
        //   · ② = 50dp（这里）⇒ 边线落在 11.25dp（15%）处，落在球内可读的位置。
        // 所以 ② 用 InnerHeight、**并且带 rim**：上下各有一条边，和左右两端的圆角端
        // 一样"有东西可折"。少录 10dp 也少一点每帧开销。
        .height(InnerHeight)
        .fillMaxWidth()
        .glassRim(shape = barShape, alpha = 0.85f)
        .padding(horizontal = 4.dp)
        .graphicsLayer(colorFilter = ColorFilter.tint(LotusPalette.Ink)),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LotusTabIcons(tabs = tabs, onSelect = onSelect)
    }

    // ---- ③ 玻璃泡 ----
    //
    // ## 单层，照上游：`combined(页面, ②)` + **只 lens**
    //
    // 泡自己只做折射：不调用 blur / vibrancy，也不铺霜面。
    //
    // 采样源是 `combined(页面, ②)` 而不是只有 ②：页面那半是**全屏**的，lens 往边缘外
    // 采样时永远有像素可采 ⇒ 不会在泡的上下两面粘出"半截齿轮"那种边界残片。
    // ⚠️ 别再改成"只采样 ②"或"只采样图标层" —— 那正是残片的来源（纹理比泡小）。
    val pillLayerBlock: GraphicsLayerScope.() -> Unit = {
      scaleX = dampedDragAnimation.scaleX
      scaleY = dampedDragAnimation.scaleY
      // 拉丝只走 **水平**：以前 velocity 还乘进 scaleY（`* 1f - pinch`），
      // 拖动时速度每帧都在变 ⇒ 球的高度每帧被压一下、弹一下，读出来就是
      // 「球一直在上下波动，不稳定」。垂直方向的形变交给 dampedDragAnimation 自己的 scaleY。
      val velocity = dampedDragAnimation.velocity / 10f
      val stretch = (abs(velocity) * 0.75f).fastCoerceIn(0f, 0.2f)
      scaleX /= 1f - stretch
    }
    Box(
      Modifier
        .padding(horizontal = 4.dp)
        .graphicsLayer {
          translationX =
            if (isLtr) {
              dampedDragAnimation.value * tabWidth + panelOffset
            } else {
              size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
            }
        }
        .then(dampedDragAnimation.modifier)
        .height(InnerHeight)
        .fillMaxWidth(1f / tabsCount)
    ) {
      Box(
        Modifier.fillMaxSize().drawBackdrop(
          backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
          shape = { Capsule() },
          effects = {
            val progress = dampedDragAnimation.pressProgress
            // 零模糊：选中泡始终是透明的，只靠静止边缘折射 + 按压增强区分。
            val resting = 0.55f + 0.45f * progress
            lens(
              10f.dp.toPx() * resting,
              14f.dp.toPx() * resting,
              chromaticAberration = true,
            )
          },
          highlight = {
            Highlight.Default.copy(alpha = 0.18f + 0.32f * dampedDragAnimation.pressProgress)
          },
          shadow = { Shadow(alpha = 0f) },
          innerShadow = { InnerShadow(radius = 0f.dp, alpha = 0f) },
          layerBlock = pillLayerBlock,
          onDrawSurface = {},
        )
      )
    }
  }
}

/**
 * 四枚静态图标。可见层（①）和折射源层（②）**共用同一个实现**，
 * 否则采样的源和眼睛看到的位置对不上 = 重影。
 *
 * 这里刻意**不做**按压/相融响应 —— 用户口径「不需要什么靠近扭那些」。
 */
@Composable
private fun RowScope.LotusTabIcons(tabs: List<LotusTab>, onSelect: (String) -> Unit) {
  tabs.forEach { tab ->
    val interaction = remember { MutableInteractionSource() }
    Box(
      Modifier
        .clip(Capsule())
        .clickable(
          interactionSource = interaction,
          indication = null,
          role = Role.Tab,
          onClick = { onSelect(tab.key) },
        )
        .fillMaxHeight()
        .weight(1f),
      contentAlignment = Alignment.Center,
    ) {
      LotusIcon(
        glyph = tab.glyph,
        tint = LotusPalette.InkMuted,
        modifier =
          Modifier.size(IconSize)
            .semantics { contentDescription = tab.label },
      )
    }
  }
}

/**
 * 三个 tab。顺序即底栏左右顺序。
 *
 * ⚠️ 记忆和人设**合成一个"文件夹"**（用户口径：「中间那两个合并成一个图标…图标就选择文件吧，
 * 因为 memory 也就是记忆，应该包含在 MD 中的某个文件里面，或者就在一个目录下」）。
 * 所以别再拆回"记忆 / 人设"两枚 —— 那两样本来就是同一批 `.md` 文件，分开放反而找不到。
 */
enum class LotusTab(val key: String, val label: String, val glyph: LotusGlyph) {
  Chat("chat", "对话", LotusGlyph.Chat),
  Files("files", "MD", LotusGlyph.Folder),
  // ⚠️ 底栏这一格**保持齿轮**。用户口径：「不是设置，你图标不要变…我是说左上角的[那枚]改成我们的绘图」。
  //    小莲图标（[LotusGlyph.Lotus]）留给左上角那枚钮用。
  Settings("settings", "设置", LotusGlyph.Settings),
}

/** 胶囊外栏高度。用户两次要求"做大一点"，从 52 提到 60。 */
internal val BarHeight = 64.dp

/** 内层（tab 内容层与玻璃泡）高度 = 外栏 - 上下各 5dp。 */
internal val InnerHeight = 56.dp



/** 底栏离安全区底边的距离。定稿图（ui-glass-13）里这条留白 ≈ 屏高 13%，用户两次要求"往上移"。 */
internal val BarMargin = 96.dp

/** 胶囊宽度。定稿图里底栏只占屏宽 ~47%，不是整条通栏；合并成 3 格后再加宽一点。 */
internal val BarWidth = 232.dp

private val IconSize = 22.dp
