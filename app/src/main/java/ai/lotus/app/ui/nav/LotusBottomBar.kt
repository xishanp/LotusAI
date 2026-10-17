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
 * 🔒 **底栏玻璃泡 —— 现行标准配方 v3（2026-10-16 晚，照上游逐行对齐）**
 *
 * 上游：`Kyant0/AndroidLiquidGlass`（Apache-2.0），分支 `kmp`，文件
 * `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt`。
 * **这一版的三个节点就是照它还原的**，别再凭感觉调。
 *
 * ## 三个节点（顺序不能乱：① 可见 → ② 录制 → ③ 泡）
 *
 * 1. **① 外栏（可见）**：`drawBackdrop(页面) { vibrancy(); blur(...); lens(...) }` + 霜面 + 描边 + 图标。
 * 2. **② 折射源（`alpha(0f)` + `layerBackdrop(tabsBackdrop)`，眼睛看不见）**：
 *    把**整条底栏的玻璃再画一遍**（`vibrancy() + blur(...) + 表面色`），
 *    **图标画在这层玻璃之上**，末尾 `ColorFilter.tint` **只染图标**。
 * 3. **③ 玻璃泡**：`drawBackdrop(backdrop = combined(页面, ②)) { lens(...) }` —— **只折，不糊**。
 *
 * ## 为什么必须是这样（血泪，别重复试错）
 *
 * - 用户口径「背景只有折射没有模糊」：**模糊在 ② 里**（那层玻璃把页面糊了一遍），不在泡里。
 *   所以泡里折到的是"已经糊好的底"，而图标是纹理里**本来就清晰**的像素 —— 互不干扰。
 *   · 把 `blur` 加到**泡自己**那层 → 图标跟着糊（用户：「像在一块玻璃后面」）——**错**。
 *   · 把 ② 只当"图标层"录（前几版就是这么写的）→ 泡里没模糊、只剩图标 ——**也错**。
 * - 用户口径「泡的上下两面粘齿轮残片」：采样源只有 ②（纹理比泡小）时，lens 往边缘外
 *   采样就采到边界残留。上游采样 `combined(页面, ②)` —— **页面那半是全屏的**，永远有像素可采。
 *   所以 ③ 的采样源**必须是 combined(页面, ②)**，不许只写 ②。
 * - `lens` 上限定在按下 14dp/20dp：再大就把图标自己压进边缘折射带里（= 残影）。
 *
 * 逐字备份：`output/golden/LotusBottomBar.LOCKED-v3-2026-10-16.kt`
 * 基线图：`output/golden/baseline-v3-{rest,pressed-home,pressed-settings}.png`
 * 历史：v1（单层泡）与 v2（自创"外壳层"）都已被上面几条否掉，只作存档。
 */
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

    // ★ 抄 NASclaw：栏体表面 = 画布色。
    // NASclaw 那份是 `canvas.copy(alpha = 0.4f)`（它的深色画布上 40% 才够"有面"）。
    // 我们是纯白画布，同样的 40% 叠上去读成一块不透明的灰板 ——
    // 用户口径「底栏背景太不透明了 整体透一些」。这里压到 0.20。
    val containerColor = LotusPalette.Canvas.copy(alpha = 0.10f)

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
            // ★ 抄 NASclaw（ai.openclaw.app.ui.NasBottomBar，上游原版参数）：
            //   vibrancy() + blur(8dp) + lens(24dp, 24dp)
            // 用户口径：「找一个叫 NASclaw 的底栏，那个底栏做的就非常完美 你直接抄外观，抄得一模一样」。
            vibrancy()
            // NASclaw 那档是 blur(8dp) —— 它背后是深色/有图的画布，8dp 读作通透；
            // 我们背后是纯白，8dp 就成了一块磨砂（用户口径「现在还像个磨砂玻璃」）。
            // 所以只把**模糊**换回我们的低模糊配方（≈2.56dp），折射仍是 NASclaw 的 24dp。
            blur(16f.dp.toPx() * 0.5f * LiquidBlurScale)
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
          // ★ 抄 NASclaw：表面色 = 画布色 40%（不是我们的渐变霜面）。
          onDrawSurface = { drawRect(containerColor) },
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
    // ⚠️⚠️ **它不只是图标**：它是把"整条底栏的玻璃"再画一遍 —— `vibrancy() + blur(...)`
    //     + 表面色，**图标画在这层玻璃之上**。于是录进 [tabsBackdrop] 的纹理是
    //      "糊过的底 + 清晰的图标"。
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
            vibrancy()
            // 折射源与栏体同一档低模糊（同上）。
            blur(16f.dp.toPx() * 0.5f * LiquidBlurScale)
            // 上游这里还有一发随按压长大的 lens：泡里那层玻璃自己也在"鼓"。
            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
          },
          highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
          shadow = { Shadow(alpha = 0f) },
          innerShadow = { InnerShadow(radius = 0f.dp, alpha = 0f) },
          onDrawSurface = { drawRect(containerColor) },
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
    // 泡自己**不许加 blur / vibrancy / 霜面**：模糊已经在 ② 里做完了，这里再糊一次
    // 就是"二次模糊"（用户否过的毛玻璃）。泡只干一件事 —— 把 ② 那张纹理折一下。
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
            // 静止带基线折射（4dp/6dp），按下加深到 14dp/20dp —— 上限定在这里：
            // 再大就会把图标自己压进泡上下两面的折射带里（= 残影）。
            // ★ 抄 NASclaw：泡只有**按下的**折射（10dp·p / 14dp·p），静止时不是玻璃板。
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
          onDrawSurface = {
            val progress = dampedDragAnimation.pressProgress
            // 🔬 实验 E5：静止墨 0.06 → 0.025。
            // 原因（源码层）：泡内 = ②（糊过的页面 + 霜面）+ ③ 这层墨。① 外栏只有霜面，
            // ③ 却多压一层 6% 墨 ⇒ 泡永远比周围底栏暗一档，读出来就是「不透 / 一块实心板」。
            // 把这一层压到几乎为零，泡就和底栏同亮度，「透」才成立。
            drawRect(LotusPalette.Ink.copy(alpha = 0.025f), alpha = 1f - progress)
          },
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
