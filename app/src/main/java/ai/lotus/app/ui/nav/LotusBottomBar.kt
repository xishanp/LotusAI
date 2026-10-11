package ai.lotus.app.ui.nav

import ai.lotus.app.ui.glass.DampedDragAnimation
import ai.lotus.app.ui.glass.LiquidBlurScale
import ai.lotus.app.ui.glass.LiquidLensScale
import ai.lotus.app.ui.glass.glassRim
import ai.lotus.app.ui.glass.glassVeilBrush
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.GraphicsLayerScope
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
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
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
 * Lotus AI 底部导航（四个 tab、**只有图标没有文字**）。
 *
 * ## 这就是"稳定版"，不要再往上叠花样
 *
 * 用户口径：
 *  - 「我先别管那个图标的现在玻璃是通透了，输出光也能跟随了，但是背景模糊呢？」→ 外栏必须糊背景；
 *  - 「这两个可以了，都说了一定要加模糊效果」→ 这一版就是那个"可以了"的版本；
 *  - 「不需要什么靠近扭那些，我只要前几个稳定版本就行」→ **没有**熔颈（相融）、
 *    **没有**图标朝泡伸长、**没有**单枚图标独立按压。
 *
 * 谁要再加"相融 / metaball / 靠近扭"，先问用户。历史上那套加进去就被否了。
 *
 * ## 四层结构（照搬 上游 Kyant0 / NASclaw 已验证的组合）
 *
 *  1. **外栏**：`vibrancy + blur(8dp) + lens(24dp,24dp)` + 霜面 + 描边。
 *     全局玻璃铁律里的"模糊"就落在这里 —— 白底上它看不见，页面上有内容时它才显形。
 *  2. **折射源层**：`alpha(0f)` + `layerBackdrop(tabsBackdrop)`，只把四枚图标录进纹理，
 *     供玻璃泡折射。于是「泡盖到谁谁亮」是光学结果，不是手算的判定。
 *  3. **玻璃泡**：`drawBackdrop(rememberCombinedBackdrop(backdrop, tabsBackdrop))`，
 *     `lens(10dp·p, 14dp·p, chromaticAberration = true)`（按压长出**色散**），
 *     `layerBlock` 里按速度做 scaleX/scaleY 拉丝 —— "液态"的主要动态来源。
 *     ⚠️ 泡**不能加 `blur`**：它采样的源里就有四枚图标，糊一下图标就变成一坨灰。
 *        上游这里也只有 `lens`，没有 `blur` / `vibrancy`。
 *  4. **交互**：`DampedDragAnimation` 管阻尼 / 吸附 / 回弹 / pressProgress / scale / velocity。
 *
 * ## 三条踩过的坑（照着做，别改回去）
 *
 *  - `innerShadow` **必须恒 0**：它画在玻璃**内部**，浅色底上表现为泡中央一块灰块。
 *  - 玻璃泡 `onDrawSurface` 那层墨色（静止 6%）**必须保留**：静止态玻璃"有实体"全靠它；
 *    删掉后静止的泡完全看不见。
 *  - 玻璃组件必须是录制盒的**兄弟节点**，不能是子节点 —— 否则玻璃采样自己，
 *    `prepareTreeImpl` 无限递归 → SIGSEGV。
 *
 * ⚠️ 拉丝统一取 `abs(velocity)`：带符号的原版（往右拉长、往左缩窄）观感是"被往下拽"，
 * 用户点名否过。别改回带符号的。
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
            // ζ = 0.65：归位时保留一点点左右余震 —— 这就是"载体跟着晃"的那一下。
            animationScope.launch { offsetAnimation.animateTo(0f, spring(0.65f, 350f, 0.5f)) }
          },
          onDrag = { _, dragAmount ->
            updateValue(
              (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                .fastCoerceIn(0f, (tabsCount - 1).toFloat())
            )
            animationScope.launch {
              offsetAnimation.animateTo(
                offsetAnimation.value + dragAmount.x,
                spring(0.5f, 500f, 0.5f),
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
            vibrancy()
            // ⚠️ **和 `glassPanel` 走同一套常数，不许各写各的**（用户口径：「你把它改为全局液态
            // 玻璃，和我们那个效果一样」）。外栏取 strength = 0.5：
            //   blur = 16dp × 0.5 × LiquidBlurScale(0.32) ≈ 2.6dp   ← 只有一层雾
            //   lens = 20dp × 0.5 × LiquidLensScale(1.5)  = 15dp      ← 玻璃感靠它，带色散
            val strength = 0.5f
            val blurPx = 16f.dp.toPx() * strength * LiquidBlurScale
            val lensPx = 20f.dp.toPx() * strength * LiquidLensScale
            blur(blurPx)
            lens(lensPx * 0.62f, lensPx * 1.15f, chromaticAberration = true)
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
          // 霜面：白底上 8% 白 = 什么都没有（加白到白还是白），所以这里也必须走霜面笔刷。
          onDrawSurface = { drawRect(brush = glassVeilBrush()) },
        )
        .height(BarHeight)
        .fillMaxWidth()
        .glassRim(shape = barShape, alpha = 0.85f)
        .padding(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LotusTabIcons(tabs = tabs, onSelect = onSelect)
    }

    // ---- ② 折射源层（alpha 0，眼睛看不见，只录进 tabsBackdrop）----
    //
    // 四枚图标全录。泡盖到谁，折射出来的就是谁 —— "泡盖到谁谁亮"是光学结果，不是判定。
    //
    // 关于"残影"：上一轮把它归因成"四枚图标被放大的泡吸进邻居"，于是改成只录一枚 ——
    // 方向错了。真因在 [ai.lotus.app.ui.glass.GlassBackground]：底色画在 layerBackdrop 外面
    // ⇒ 源纹理全透明 ⇒ 玻璃不遮底 ⇒ 折射出的图标叠在原图标上。底修好之后这里必须
    // **保持四枚**，否则泡放大时邻居不会被拉进来，"液态"的横向流动感就没了。
    Row(
      Modifier
        .clearAndSetSemantics {}
        .alpha(0f)
        .layerBackdrop(tabsBackdrop)
        .graphicsLayer { translationX = panelOffset }
        .height(InnerHeight)
        .fillMaxWidth()
        .padding(horizontal = 4.dp)
        .graphicsLayer(colorFilter = ColorFilter.tint(LotusPalette.Ink)),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LotusTabIcons(tabs = tabs, onSelect = onSelect)
    }

    // ---- ③ 玻璃泡 ----
    //
    // ## 为什么是**两层**（2026-10-11 晚修：用户报「菜单栏底部被选中的地方没有实时的模糊」）
    //
    // 原来只有一层，`backdrop = combined(页面背景, 图标层)`。问题有两半：
    //  1. 采样**原始页面背景** ⇒ 泡里是没被外栏糊过的底，反而比周围更清楚，"看起来没模糊"；
    //  2. 想给泡加 blur 又不敢 —— 采样源里有四枚图标，一糊图标就成一坨灰。
    //
    // 正解是把两件事拆到两个**兄弟节点**里（顺序不能反）：
    //  - **底层**：采样外栏导出的那层玻璃（[barBackdrop]，本来就是糊的），再补一遍轻 blur
    //    ⇒ 泡里的背景比周围更软，"玻璃"一眼可辨；
    //  - **顶层**：只采样图标层（[tabsBackdrop]，透明底）做 lens 折射 ⇒ 图标**永远清晰**，
    //    只是被折了一下。顶层除图标外全透明，不会盖掉底层的模糊。
    //
    // ⚠️⚠️ 两层必须是**兄弟**，而且**各自**挂同一个 `layerBlock`。绝不能把顶层套在底层里面：
    //     `layerBlock` 的 graphicsLayer 会作用到子节点，图层的 `-offset` 补偿只会抵消它**自己**那份
    //     变换，于是图标被按着"泡的中心"整体放大位移 —— 这就是用户看到的
    //     「加粗的图标乱跳 / 菜单栏下面凭空多出来一个文件夹图标」。
    val pillLayerBlock: GraphicsLayerScope.() -> Unit = {
      scaleX = dampedDragAnimation.scaleX
      scaleY = dampedDragAnimation.scaleY
      // 拉丝对称：只压不拽，方向感交给泡的位移，避免"往哪拉都像往下掉"。
      val velocity = dampedDragAnimation.velocity / 10f
      val stretch = (abs(velocity) * 0.75f).fastCoerceIn(0f, 0.2f)
      val pinch = (abs(velocity) * 0.25f).fastCoerceIn(0f, 0.2f)
      scaleX /= 1f - stretch
      scaleY *= 1f - pinch
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
      // ---- 底层：外栏那层玻璃 + 再糊一遍 ----
      Box(
        Modifier.fillMaxSize().drawBackdrop(
          backdrop = barBackdrop,
          shape = { Capsule() },
          effects = {
            // 和外栏同一套常数（16dp × 0.5 × LiquidBlurScale）⇒ 泡里的底**比外栏更软一点**，
            // 但亮度不变（不加 vibrancy、不加霜面，免得又成一块白板）。
            blur(16f.dp.toPx() * 0.5f * LiquidBlurScale)
          },
          highlight = { Highlight.Default.copy(alpha = 0f) },
          shadow = { Shadow(alpha = 0f) },
          // 恒 0：内投影画在玻璃内部，会变成中央一块灰。
          innerShadow = { InnerShadow(radius = 0f.dp, alpha = 0f) },
          layerBlock = pillLayerBlock,
          onDrawSurface = {
            // 静止那层墨色**必须保留**：玻璃泡"有实体"全靠它。
            val progress = dampedDragAnimation.pressProgress
            drawRect(LotusPalette.Ink.copy(alpha = 0.06f), alpha = 1f - progress)
            drawRect(LotusPalette.Ink.copy(alpha = 0.03f * progress))
          },
        )
      )

      // ---- 顶层：只画图标，做折射（图标保持清晰）----
      Box(
        Modifier.fillMaxSize().drawBackdrop(
          backdrop = tabsBackdrop,
          shape = { Capsule() },
          effects = {
            val progress = dampedDragAnimation.pressProgress
            lens(
              10f.dp.toPx() * progress,
              14f.dp.toPx() * progress,
              chromaticAberration = true,
            )
          },
          highlight = {
            Highlight.Default.copy(alpha = 0.35f * dampedDragAnimation.pressProgress)
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
internal val BarHeight = 60.dp

/** 内层（tab 内容层与玻璃泡）高度 = 外栏 - 上下各 5dp。 */
internal val InnerHeight = 50.dp

/** 底栏离安全区底边的距离。定稿图（ui-glass-13）里这条留白 ≈ 屏高 13%，用户两次要求"往上移"。 */
internal val BarMargin = 96.dp

/** 胶囊宽度。定稿图里底栏只占屏宽 ~47%，不是整条通栏；合并成 3 格后再加宽一点。 */
internal val BarWidth = 232.dp

private val IconSize = 22.dp
