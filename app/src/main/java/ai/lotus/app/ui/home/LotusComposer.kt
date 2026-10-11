package ai.lotus.app.ui.home

import ai.lotus.app.ui.glass.LiquidGlassIconButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import ai.lotus.app.ui.glass.LiquidLensScale
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** 输入条高度。外层算底部留白要用，所以是 internal。 */
internal val LotusComposerHeight = 56.dp

/**
 * 玻璃输入条。
 *
 * 内部从左到右：`+` 附件 · 输入框 · `模型 · 档位` 玻璃片（点开合并面板）· `↗` 发送。
 *
 * ## 四个按钮全部走 [LiquidGlassIconButton]
 *
 * 用户口径：「所有按键玻璃全都要有折射效果……现在那个发送键就有问题，没有模糊没有折射」。
 * 所以这里不再用"图标 + 背景色"的普通按钮：
 *
 *  - **四枚全部 `nested = true`**（用户 2026-10-11 定）：「都在一个玻璃下，就不应该再分出一个
 *    玻璃……比如发送键、模型选择键……不要描边了，直接隐藏起来。但是它们是真实存在的，
 *    长按的时候会有真实反馈」。所以静止时它们**没有自己的边和面**，整个融进输入条这一块玻璃；
 *    按下才长出折射 + 色散 + 手指那点光，抬手收回。
 *  - 早先发送键用过 `veil = 0.85`（自带一块能看见的圆片），那正是"玻璃叠玻璃"的脏边来源，
 *    **不要再改回去**。
 *
 * `blendAmbient = false`：它们都**嵌在输入条这层玻璃内部**。若去采样页面背景，就会把输入条
 * 背后那页内容重新画到输入条上面 —— 等于给输入条挖了个洞。
 *
 * ## 载体联动（用户："一个东西动了，它旁边的东西应该跟着动"）
 *
 *  - **邻位相融**：某一枚按下时，紧挨着它的那枚**朝它伸过去**（拉伸 22% + 轻微压扁，
 *    锚在朝向那一侧的边上）—— 两块玻璃贴上、并成一块，就是 Apple 的"相邻玻璃熔合"；
 *  - **载体被拽**：整条玻璃输入条朝按下那一侧被"拽" 1.5dp，抬手弹回（`tug`）。
 *
 * 两样都控制在 1.5dp 以内 —— 要的是"活"，不是把输入条晃散。
 */
@Composable
fun LotusComposer(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "此刻，想说些什么？",
  /** 当前模型的思考档位名。跟模型名拼在同一块玻璃片上（用户口径：「合并吧」）。 */
  thinkingLabel: String = "",
  /**
   * 当前模型的显示名（空 = 还没选）。
   *
   * 用户口径：「怎么区分现在用的是什么模型？不可能总要点开模型页看吧。」
   * 所以 ⇄ 那枚按键**自己**变成一块写着模型名的玻璃小片 —— 入口在哪，状态就在哪，
   * 不额外占一行、也不新增一个控件。点它照旧弹模型面板。
   */
  modelLabel: String = "",
  onAttach: () -> Unit = {},
  /** 输入条**实测**高度（多行时会往上长）。外壳要用它摆输入条上方的浮层。 */
  onHeightChange: (Dp) -> Unit = {},
  /** 点那块"模型 · 档位"玻璃片。模型和思考现在是**同一块面板**，所以只有一个入口。 */
  onOpenPanel: () -> Unit = {},
  onSend: () -> Unit = {},
) {
  // ---- 多行自增高 + 玻璃被"拉长"的折射 ----
  //
  // 用户口径：「输入框输短文还好，出长文它只能左右滑；当超出范围后应该往上叠，
  // 做成上下页这样，一直往上叠，整个玻璃要跟着上下变大，变大的时候要有扭曲效果，要很明显的拉伸。」
  // 所以：文字自动换行、整条玻璃往上长（底边不动），折射系数随高度**过冲**一下再回落 ——
  // 那一下过冲就是"玻璃被拉长"的观感（spring dampingRatio 调低才有，别改成 0.9）。
  val density = LocalDensity.current
  // 上限只跟屏幕有关，不跟行数有关 —— 用户要的是"无限长"，不是"最多几行"。
  val screenMax = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
  var measured by remember { mutableStateOf(LotusComposerHeight) }
  // 「要不要切成两排」看的是**文字本身有多宽**，不是量出来的条高。
  // ⚠️ 之前用条高判断，出了个回不去的 bug：两排布局自己就比一排高，
  //    于是 measured 一旦变大就永远 >56dp，multi 永远为真 —— 用户把字全删了，
  //    玻璃还是两排高，缩不回去（用户原话：「我最后全删了，它却没回到最初的样子」）。
  val measurer = rememberTextMeasurer()
  val chipW = (listOf(modelLabel.trim(), thinkingLabel.trim()).filter { it.isNotEmpty() }
      .joinToString(" · ").length * 6.6f + 22f).coerceIn(56f, 130f).dp
  val rowWidth = LocalConfiguration.current.screenWidthDp.dp - 32.dp
  // 一排时文字能用多宽：整条 - 附件键 - 模型片 - 间距 - 发送键 - 内边距。
  val rowTextWidth = (rowWidth - 34.dp - chipW - 4.dp - 38.dp - 16.dp).coerceAtLeast(72.dp)
  val needWidth = remember(value, rowTextWidth) {
    if (value.isEmpty()) 0
    else
      measurer
        .measure(
          androidx.compose.ui.text.AnnotatedString(value),
          style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
        )
        .size.width
  }
  val multi = value.contains('\n') || needWidth > with(density) { rowTextWidth.toPx() }
  val grow = if (multi) 1f else 0f
  // 长高这件事本身：用来把按键顺下去、把圆角收一点 —— 平滑、不弹。
  val growAnim by
    animateFloatAsState(
      targetValue = grow,
      animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
      label = "composerGrow",
    )
  // 拉伸：**只在长高的那一瞬**踢一下，然后弹回去。
  //
  // 为什么不做成"越高折射越强"：试过（1.5 和 0.35 两档都试了），静止时整块玻璃会变成哈哈镜 ——
  // 背后的画面被拉成大花脸，通透感全没了（用户最恨的就是这个）。改成一次性过冲之后：
  // 长高的过程里玻璃明显被拽长一下，静止时又是那块干净的玻璃。
  var kick by remember { mutableStateOf(0f) }
  LaunchedEffect(measured) {
    if (measured <= LotusComposerHeight) {
      kick = 0f
    } else {
      kick = 1f
      kotlinx.coroutines.delay(150)
      kick = 0f
    }
  }
  val stretch by
    animateFloatAsState(
      targetValue = kick,
      animationSpec = spring(dampingRatio = 0.3f, stiffness = 320f),
      label = "composerStretch",
    )
  val radius by
    animateDpAsState(
      targetValue = if (growAnim < 0.1f) LotusComposerHeight / 2 else 24.dp,
      animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
      label = "composerRadius",
    )
  val shape = RoundedCornerShape(radius)

  // 哪一枚按键正被按住。0 = 附件，1 = 模型/档位，2 = 发送。
  var pressedSlot by remember { mutableStateOf<Int?>(null) }

  // ---- 寄生按键按下时"糊谁"（用户 2026-10-11 报的问题 2）----
  //
  // 用户口径：「我点那个发送键之后，长按刚好会有一些字段到发送键下面，它又围成一个玻璃，
  // 不应该会有模糊效果吗？连模糊效果都没有」。
  //
  // 输入条里那三枚按键 `nested = true`（没有自己的壳，见 [LiquidGlassIconButton] 的铁律），
  // 所以它们按下时能糊的东西只有两样，这里各录一层：
  //  - [barBackdrop]：输入条自己画出来的那层玻璃（`glassPanel(exportedBackdrop = …)` 导出）；
  //  - [underContent]：**可能滑到按键底下的兄弟内容** —— 现在只有那块"模型 · 档位"玻璃片
  //    （邻位相融会把它朝按下的那一侧拉过去，正压在邻居上面）。
  //
  // 两样都录进后台纹理之后，按键按下时把它们重新糊一遍 + 折射。这样滑进来的字是**真的糊掉**，
  // 而不是"压在按键上面、一个像素都没变"。
  val barBackdrop = rememberLayerBackdrop()
  val underContent = rememberLayerBackdrop()
  val underGlass = rememberCombinedBackdrop(barBackdrop, underContent)

  // 载体被拽：按下 → 整条玻璃朝那一侧偏 1.5dp；抬手 → 带一点回弹地归零。
  val tug by
    animateFloatAsState(
      targetValue =
        when (pressedSlot) {
          null -> 0f
          0 -> -1.5f
          1 -> 0.4f
          else -> 1.5f
        },
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
      label = "composerTug",
    )

  // ---- 文本域 ----
  //
  // ⚠️ 必须用 movableContentOf：多行时输入框要从"挤在按键左边"搬到"独占一整行"，
  //    普通 if/else 换结构会把 BasicTextField 从组合里拆掉重建 —— 焦点和键盘一起丢，
  //    用户正打字时键盘会自己收起来。movableContent 就是官方给这种情况的解法。
  //    里面的值全部走 rememberUpdatedState，否则记住的内容会永远停在第一次的那些参数上。
  val curValue by rememberUpdatedState(value)
  val curOnChange by rememberUpdatedState(onValueChange)
  val curSend by rememberUpdatedState(onSend)
  val field = remember {
    movableContentOf {
      Box(modifier = Modifier.fillMaxWidth()) {
        if (curValue.isEmpty()) {
          Text(
            text = placeholder,
            color = LotusPalette.InkMuted,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = curValue,
          onValueChange = { curOnChange(it) },
          // 多行：超出宽度就换行，整条玻璃**一直往上叠**（用户口径：「你这还是有限高啊，你应该做的无限长」）。
          // 唯一的刹车在容器高度上（见 heightIn 的 max）：真长到顶天才在框内滚。
          singleLine = false,
          maxLines = 20,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { curSend() }),
          textStyle = LocalTextStyle.current.copy(color = LotusPalette.Ink, fontSize = 15.sp),
          cursorBrush = SolidColor(LotusPalette.Ink),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  // ---- 三枚按键。两种布局共用同一份代码，免得改一处漏一处。----
  val plusButton: @Composable () -> Unit = {
    LiquidGlassIconButton(
      onClick = onAttach,
      modifier = Modifier.merge(index = 0, pressedSlot = pressedSlot),
      size = 34.dp,
      veil = 0f,
      nested = true,
      underGlass = underGlass,
      lensStrength = 0.9f,
      onPressedChange = { down -> pressedSlot = if (down) 0 else null },
    ) { tint ->
      ComposerGlyph(LotusGlyph.Add, "添加图片", tint)
    }
  }

  // 【模型 · 档位】一整块玻璃片：入口在哪、状态就在哪。
  // ⚠️ 原来是 "⚡ 档位 + 分隔线 + ⇄ 模型" 三个东西；用户要求合并成一个，
  //    并且要让这一块**自己就能读出思考等级**。
  val modelButton: @Composable () -> Unit = {
    val label =
      listOf(modelLabel.trim(), thinkingLabel.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
    if (label.isEmpty()) {
      LiquidGlassIconButton(
        onClick = onOpenPanel,
        modifier = Modifier.merge(index = 1, pressedSlot = pressedSlot),
        size = 34.dp,
        veil = 0f,
        nested = true,
        // ⚠️ 只能给 [barBackdrop]：这一枚自己就是 [underContent] 的录制源，
        //    采样"包含自己"的那层会无限递归（父节点录 → 子节点画 → 又要父节点录）。
        underGlass = barBackdrop,
        lensStrength = 0.9f,
        onPressedChange = { down -> pressedSlot = if (down) 1 else null },
      ) { tint ->
        ComposerGlyph(LotusGlyph.Tune, "选择模型与思考等级", tint)
      }
    } else {
      // 宽度按字数算，56~130dp：够写 "gpt-6-luna · ultra"，再长就省略号。
      val chipWidth = (label.length * 6.6f + 22f).coerceIn(56f, 130f).dp
      LiquidGlassIconButton(
        onClick = onOpenPanel,
        // ⚠️ 这一层是"录"不是"采"：把这块玻璃片的实际画法录进 [underContent]，
        //    给旁边的 + 和发送键当"罩下"纹理用。它自己也照常画。
        modifier =
          Modifier.layerBackdrop(underContent)
            .merge(index = 1, pressedSlot = pressedSlot)
            .width(chipWidth),
        size = 34.dp,
        shape = RoundedCornerShape(17.dp),
        veil = 0.5f,
        nested = true,
        underGlass = barBackdrop,
        lensStrength = 0.9f,
        onPressedChange = { down -> pressedSlot = if (down) 1 else null },
      ) { tint ->
        Text(
          text = label,
          color = tint.copy(alpha = 0.85f),
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier =
            Modifier.padding(horizontal = 9.dp)
              .semantics { contentDescription = "当前 $label，点按切换" },
        )
      }
    }
  }

  val sendButton: @Composable () -> Unit = {
    LiquidGlassIconButton(
      onClick = onSend,
      modifier = Modifier.merge(index = 2, pressedSlot = pressedSlot),
      size = 38.dp,
      veil = 0.85f,
      nested = true,
      underGlass = underGlass,
      lensStrength = 1.2f,
      onPressedChange = { down -> pressedSlot = if (down) 2 else null },
    ) { tint ->
      ComposerGlyph(LotusGlyph.Send, "发送", tint)
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .graphicsLayer { translationX = tug.dp.toPx() }
        .onSizeChanged { size ->
          val h = with(density) { size.height.toDp() }
          if (h != measured) {
            measured = h
            onHeightChange(h)
          }
        }
        // 长到接近整屏时收手：再长就在框内滚，别把玻璃顶到屏幕外看不见。
        .heightIn(min = LotusComposerHeight, max = screenMax)
        .glassPanel(
          shape = shape,
          // 把这层玻璃自己画出来的样子导出，给里面那几枚寄生按键按下时"罩下"用。
          exportedBackdrop = barBackdrop,
          // 不叠实色：白叠白 = 一张白纸片。玻璃的"面"由霜面 + 轻微高光边负责。
          strength = 0.55f,
          // ⚠️ 边要"轻"。这条大胶囊上 rimAlpha = 1 会读成一根塑料壳（用户否过两次）。
          rimAlpha = 0.5f,
          // ⚠️ 拉伸就在这一行，但**只在长高的那一瞬**（stretch 会自己弹回 0）。
          lensScale = LiquidLensScale * (1f + 0.45f * stretch),
        )
        .padding(horizontal = 6.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    if (!multi) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        plusButton()
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) { field() }
        modelButton()
        Spacer(Modifier.width(4.dp))
        sendButton()
      }
    } else {
      // 多行：文字独占上面**一整行**（不再被右边那几枚按键挤成窄柱），按键退到下面一排。
      // 用户口径：「都开始输入文字了，右边那一大块都拿来干嘛的？」
      Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = false,
          maxLines = 20,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { onSend() }),
          textStyle = LocalTextStyle.current.copy(color = LotusPalette.Ink, fontSize = 15.sp),
          cursorBrush = SolidColor(LotusPalette.Ink),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        plusButton()
        Spacer(Modifier.weight(1f))
        modelButton()
        Spacer(Modifier.width(4.dp))
        sendButton()
      }
    }
  }
}

/**
 * 邻位**相融**：旁边的按键被按下时，这枚**朝它伸过去**（而不是被推开）。
 *
 * 做法：把缩放锚点放到**朝向那一侧的边**上，然后横向拉伸 —— 于是玻璃的这边保持不动、
 * 另一边朝邻居长出去 22%，和邻居那层玻璃贴上、并成一块。再配 6% 的纵向压扁，
 * 看起来才像"两块液体被吸到一起"，而不是被拉开。
 *
 * 用 `graphicsLayer` 而不是 `Modifier.offset`：只影响绘制、不触发重新布局，
 * 所以相融过程中整条输入条的内部间距不会抖。
 */
@Composable
private fun Modifier.merge(index: Int, pressedSlot: Int?): Modifier {
  val adjacent = pressedSlot != null && abs(index - pressedSlot) == 1
  // 锚点：邻居在右边 ⇒ 锚自己的左边；邻居在左边 ⇒ 锚自己的右边。这样只会朝邻居长。
  val anchorX = if (!adjacent) 0.5f else if (pressedSlot > index) 0f else 1f
  val k by
    animateFloatAsState(
      targetValue = if (adjacent) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
      label = "composerMerge",
    )
  return this.graphicsLayer {
    transformOrigin = TransformOrigin(anchorX, 0.5f)
    scaleX = 1f + 0.22f * k
    scaleY = 1f - 0.06f * k
  }
}

@Composable
private fun ComposerGlyph(glyph: LotusGlyph, description: String, tint: Color) {
  LotusIcon(
    glyph = glyph,
    tint = tint,
    modifier = Modifier.size(20.dp).semantics { contentDescription = description },
  )
}
