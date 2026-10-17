package ai.lotus.app.ui.home

import ai.lotus.app.data.Conversation
import ai.lotus.app.ui.glass.LiquidGlassIconButton
import ai.lotus.app.ui.glass.floatGlass
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.glass.glassRim
import ai.lotus.app.ui.glass.pressLiquid
import ai.lotus.app.ui.glass.rowPress
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 历史列表里的时间：刚刚 / N 分钟前 / N 小时前 / 昨天 / 月-日。 */
private fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
  if (at <= 0L) return ""
  val diff = now - at
  val minute = 60_000L
  val hour = 60 * minute
  return when {
    diff < minute -> "刚刚"
    diff < hour -> "${diff / minute} 分钟前"
    diff < 12 * hour -> "${diff / hour} 小时前"
    else -> {
      val cal = Calendar.getInstance()
      val today = cal.get(Calendar.DAY_OF_YEAR)
      val year = cal.get(Calendar.YEAR)
      cal.time = Date(at)
      val sameYear = cal.get(Calendar.YEAR) == year
      val yesterday = (today - cal.get(Calendar.DAY_OF_YEAR) + 1) % 365 == 0
      when {
        yesterday -> "昨天"
        sameYear -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(at))
        else -> SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date(at))
      }
    }
  }
}

/**
 * 会话历史浮层。
 *
 * 全 App 同一套液态玻璃（`glassPanel`）：轻模糊 + 边缘折射 + 色散 + 白描边 + 柔影。
 * 列表按 [Conversation.updatedAt] 倒序 —— 最近聊的排最上面。
 *
 * ⚠️ 它是**悬浮**的：不占布局高度（用户口径：「液态玻璃就是为了放大空间代替顶栏」）。
 * 所以调用方必须把它放进 `GlassBackground` 的 content 槽，别塞进页面 Column。
 */
@Composable
fun ConversationHistoryPanel(
  conversations: List<Conversation>,
  currentId: String?,
  onOpen: (String) -> Unit,
  onNewChat: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(24.dp)
  val sorted = remember(conversations) { conversations.sortedByDescending { it.updatedAt } }

  Column(
    modifier =
      modifier
        .width(304.dp)
        .heightIn(max = 430.dp)
        // 浮层玻璃统一配方（floatGlass）：不发白霜、描边浓度跟全 App 一致。
        // 菜单背后经常直接压着正文；保留低模糊的观感，但比页面内玻璃略高，
        // 让列表文字不会从菜单里透出来叠成重影。
        .floatGlass(shape = shape, strength = 0.55f, elevation = 14.dp, blurScale = 0.58f)
        .padding(vertical = 8.dp),
  ) {
    // 标题行 + 新建
    Row(
      modifier = Modifier.fillMaxWidth().height(46.dp).padding(start = 18.dp, end = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "会话",
        color = LotusPalette.InkMuted,
        fontSize = 13.sp,
        modifier = Modifier.weight(1f),
      )
      val newChatP = rowPress(RoundedCornerShape(15.dp), scale = 1.06f)
      Box(
        modifier =
          Modifier.height(30.dp)
            .then(newChatP.modifier)
            .clickable(interactionSource = newChatP.interaction, indication = null) {
              onNewChat()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text("新聊天", color = LotusPalette.Ink, fontSize = 13.sp)
      }
    }

    if (sorted.isEmpty()) {
      Text(
        text = "还没有历史会话",
        color = LotusPalette.InkFaint,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
      )
      return@Column
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      sorted.forEach { c ->
        val isCurrent = c.id == currentId
        val convP = rowPress(RoundedCornerShape(14.dp))
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .then(convP.modifier)
              .clickable(
                interactionSource = convP.interaction,
                indication = null,
              ) { onOpen(c.id) }
              .height(58.dp)
              .padding(horizontal = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = c.displayTitle,
              color = LotusPalette.Ink,
              fontSize = 15.sp,
              fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                buildString {
                  append("${c.messages.size} 条消息")
                  relativeTime(c.updatedAt).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                },
              color = LotusPalette.InkFaint,
              fontSize = 11.sp,
              modifier = Modifier.padding(top = 2.dp),
            )
          }
          Box(
            modifier =
              Modifier.size(7.dp)
                .clip(CircleShape)
                .background(if (isCurrent) LotusPalette.Ink else Color.Transparent),
          )
        }
      }
    }
  }
}

/**
 * 模态弹层的遮罩浓度。
 *
 * ⚠️ 它跟 [ModalGlassDim] 是**一对**：scrim 把页面压暗多少，弹层自身就压暗多少 ——
 * 两边亮度接上，弹层才不会读成"贴上去的一张白纸"（用户报的"颜色跟背景不一样"）。
 * 别只改一个。
 */
internal const val ModalScrimAlpha: Float = 0.10f

/** 模态弹层自身的压暗量。略小于 [ModalScrimAlpha]：玻璃本来就该比背景亮一点点。 */
internal const val ModalGlassDim: Float = 0.08f

/**
 * 居中的玻璃输入框弹层（改会话名字用）。
 *
 * 为什么不用 `AlertDialog`：它自带 Material 的白卡与投影，和白底液态玻璃是两套语言。
 * 这里就一层 scrim + 一块玻璃，跟全 App 其余浮层完全同材质。
 */
@Composable
fun GlassPromptDialog(
  title: String,
  initial: String,
  confirmLabel: String,
  modifier: Modifier = Modifier,
  hint: String = "",
  maskInput: Boolean = false,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var value by remember(initial) { mutableStateOf(initial) }
  val shape = RoundedCornerShape(26.dp)

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    // scrim：压暗一点点 + 点它关闭。别用纯黑 0.4，白底上会脏。
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = ModalScrimAlpha))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { onDismiss() }
    )

    Column(
      modifier =
        Modifier.padding(horizontal = 32.dp)
          .fillMaxWidth()
          // 模态弹层：最容易"跟背景不一样"的那块。统一配方 + 用 dim 把自身压暗到
          // 跟被 scrim 压暗的页面同一个亮度（差值抹掉了，"颜色不一样"才会真的消失）。
          .floatGlass(
            shape = shape,
            strength = 0.7f,
            elevation = 18.dp,
            dim = ModalGlassDim,
          )
          .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
      Text(text = title, color = LotusPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(12.dp))

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            // 弹层里不画第二块玻璃：输入框只用一层淡墨底标出"这里能打字"，不描边。
            .background(LotusPalette.Ink.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        BasicTextField(
          value = value,
          onValueChange = { value = it },
          singleLine = true,
          textStyle = androidx.compose.ui.text.TextStyle(color = LotusPalette.Ink, fontSize = 15.sp),
          cursorBrush = SolidColor(LotusPalette.Ink),
          visualTransformation =
            if (maskInput) PasswordVisualTransformation() else VisualTransformation.None,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (hint.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(text = hint, color = LotusPalette.InkMuted, fontSize = 11.sp)
      }

      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogButton(text = "取消", primary = false, modifier = Modifier.weight(1f)) { onDismiss() }
        DialogButton(text = confirmLabel, primary = true, modifier = Modifier.weight(1f)) {
          onConfirm(value.trim())
        }
      }
    }
  }
}

@Composable
private fun DialogButton(
  text: String,
  primary: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(15.dp)
  // ⚠️ 主按钮**不能**再叠 [pressLiquid]：那个折射会把采到的背景重画一遍，
  //    正好盖住实心墨色底 —— 中间变空心、四周留一圈黑（用户报过两次的"空心黑描边"）。
  //    主按钮的按压反馈改成**只缩放**；次按钮不走实心底，照旧走薄折射。
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val k by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "dialogBtnPress",
    )
  Box(
    modifier =
      modifier
        .height(42.dp)
        .clip(shape)
        .then(
          if (primary) Modifier.background(LotusPalette.Ink)
          else Modifier.pressLiquid(active = k, shape = shape, scale = 0.97f)
        )
        .then(
          if (primary) {
            Modifier.graphicsLayer {
              // 同上：按下放大，不缩小。
              scaleX = 1f + 0.03f * k
              scaleY = 1f + 0.03f * k
            }
          } else {
            Modifier
          }
        )
        .clickable(interactionSource = interaction, indication = null) { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (primary) Color.White else LotusPalette.Ink,
      fontSize = 14.sp,
    )
  }
}

/**
 * 首页/聊天页左上角那枚历史钮。**悬浮**，不占顶栏高度。
 *
 * ⚠️ 用户口径：「不是，你放错地方了，你怎么放在了历史绘画那个地方？你给我改回去」——
 *    所以这里回到 ☰。那张举着扳手的立绘（`lotus_settings`）放去**设置页最上面**，
 *    照抄文件页（MD）最上面那张立绘的排法。别再把立绘塞进这枚钮。
 */
@Composable
fun FloatingHistoryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  LiquidGlassIconButton(
    onClick = onClick,
    modifier = modifier,
    size = 42.dp,
    veil = 0.55f,
    blendAmbient = true,
    lensStrength = 1f,
  ) { tint ->
    LotusIcon(
      glyph = LotusGlyph.Menu,
      tint = tint.copy(alpha = 0.72f),
      modifier = Modifier.size(19.dp),
    )
  }
}

/**
 * 右上角**唯一**那枚玻璃钮：外圈画真实用量，中间是 ⋯。
 *
 * 用户口径：「聊天界面右上角那个更多太啰嗦了吧，而且太大了……还有上下文，你看怎么样能合并一下」。
 * 以前这里是**两枚** 42dp 玻璃（一个百分比表盘 + 一个三点），面板也是两个 —— 又占地方又重复。
 * 合成一枚之后：**用量在做外圈、动作在中间**，点一下开同一块面板（[GlassChatPanel]）。
 */
@Composable
fun FloatingMoreGauge(ratio: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val safe = ratio.coerceIn(0f, 1f)
  // 过 70% 就把环加粗、加浓 —— 提醒"该压缩了"，但绝不自动替用户花钱压缩。
  val warm = safe > 0.7f
  LiquidGlassIconButton(
    onClick = onClick,
    modifier = modifier,
    size = 40.dp,
    veil = 0.32f,
    blendAmbient = true,
    lensStrength = 1f,
  ) { tint ->
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = (if (warm) 2.4f else 1.8f).dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
          color = tint.copy(alpha = 0.13f),
          startAngle = -90f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (safe > 0.004f) {
          drawArc(
            color = tint.copy(alpha = if (warm) 0.95f else 0.72f),
            startAngle = -90f,
            sweepAngle = 360f * safe,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
        }
      }
      LotusIcon(
        glyph = LotusGlyph.More,
        tint = tint.copy(alpha = 0.78f),
        modifier = Modifier.size(15.dp),
      )
    }
  }
}

/**
 * 聊天页右上角**一块**面板：上面是用量与压缩/长期记忆，下面是会话动作。
 *
 * 用户口径：「更多太啰嗦了吧，而且太大了，根本没有这么多字，你放这么大的空间……还有上下文，
 * 你看怎么样能合并一下」。所以：两面板合一、行高压到 38dp、删掉每条动作下面那行解释
 * （信息写成行尾的小字，不再另起一行）。
 *
 * ⚠️ 用量数字必须是**服务端报的** `prompt_tokens`；服务端不回 usage 时才退回估算，
 *    并且要明写「估算」二字 —— 不许把估的当成真的给用户看。
 */
@Composable
fun GlassChatPanel(
  usedTokens: Int,
  windowTokens: Int,
  real: Boolean,
  messageCount: Int,
  summaryChars: Int,
  busy: Boolean,
  /** 「记住这段」正在跑（要一次模型调用，得让用户看得见）。 */
  rememberBusy: Boolean = false,
  /** 上一次「记住这段」的结果，写在行尾。 */
  rememberHint: String = "",
  modifier: Modifier = Modifier,
  onCompress: () -> Unit,
  onRemember: () -> Unit = {},
  /** 更新日志（更多里的一项）。 */
  onChangelog: () -> Unit = {},
  /** 「它都有哪些工具」—— 独立一页列出来（用户口径：「工具展示：显示它都有哪些工具」）。 */
  onTools: () -> Unit = {},
  /** 「工具」那一行右边的小字：几项已配好 / 共几项。 */
  toolsHint: String = "",
  onNewChat: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  val shape = RoundedCornerShape(22.dp)
  val ratio = if (windowTokens > 0) (usedTokens.toFloat() / windowTokens).coerceIn(0f, 1f) else 0f
  val warm = ratio > 0.7f

  Column(
    modifier =
      modifier
        .width(248.dp)
        // 面板背后可能是聊天气泡正文。低模糊口径不变，但必须足够把正文从“可读”
        // 压成“轮廓”，否则透明玻璃会和面板文字叠在一起。
        .floatGlass(shape = shape, strength = 0.55f, elevation = 14.dp, blurScale = 0.58f)
        // 吃掉落在面板空白处的点击，别让它们穿到底下的 scrim 上把面板关掉。
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    // ---- 上下文：一行标题 + 一行数字，比原来省掉一整块高度 ----
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("上下文", color = LotusPalette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.width(6.dp))
      Text(
        text = if (real) "实测" else "估算",
        color = LotusPalette.InkFaint,
        fontSize = 10.sp,
        modifier = Modifier.weight(1f),
      )
      Text(
        text =
          "${ai.lotus.app.data.formatTokens(usedTokens)} / ${ai.lotus.app.data.formatTokens(windowTokens)}",
        color = if (warm) LotusPalette.Ink else LotusPalette.InkMuted,
        fontSize = 12.sp,
      )
    }

    Spacer(Modifier.height(7.dp))
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(3.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(LotusPalette.Ink.copy(alpha = 0.10f))
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth(ratio.coerceAtLeast(0.015f))
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LotusPalette.Ink.copy(alpha = if (warm) 0.72f else 0.45f))
      )
    }

    Spacer(Modifier.height(7.dp))
    Text(
      text =
        buildString {
          append("$messageCount 条")
          append(" · 剩 ${ai.lotus.app.data.formatTokens((windowTokens - usedTokens).coerceAtLeast(0))}")
          if (summaryChars > 0) append(" · 摘要 ${summaryChars} 字")
          if (warm) append(" · 该压缩了")
        },
      color = if (warm) LotusPalette.Ink else LotusPalette.InkFaint,
      fontSize = 10.sp,
    )

    Spacer(Modifier.height(9.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      PanelPill(
        text = if (busy) "压缩中…" else "压缩",
        enabled = !busy && messageCount > 2,
        emphasized = warm,
        onClick = onCompress,
      )
      PanelPill(
        text = if (rememberBusy) "记忆…" else "记住这段",
        enabled = !busy && !rememberBusy && messageCount > 0,
        emphasized = false,
        onClick = onRemember,
      )
    }
    if (rememberHint.isNotBlank()) {
      Spacer(Modifier.height(5.dp))
      Text(rememberHint, color = LotusPalette.InkMuted, fontSize = 10.sp)
    }

    Spacer(Modifier.height(10.dp))
    Box(
      modifier =
        Modifier.fillMaxWidth().height(0.7.dp).background(LotusPalette.Ink.copy(alpha = 0.08f))
    )
    Spacer(Modifier.height(2.dp))

    // ---- 会话动作：一行一个，行高 38dp（原来是 46dp 还带一行解释，太占地方）----
    PanelRow(if (toolsHint.isBlank()) "工具" else "工具 · $toolsHint", onClick = onTools)
    PanelRow("开新聊天", onClick = onNewChat)
    PanelRow("修改聊天名称", onClick = onRename)
    PanelRow("删除这个会话", onClick = onDelete)
  }
}

/** 合并面板里的小胶囊动作（压缩 / 记住这段）。 */
@Composable
private fun PanelPill(
  text: String,
  enabled: Boolean,
  emphasized: Boolean,
  onClick: () -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  // 2026-10-16：以前这里按下只把墨底从 4% 加到 10% —— 那就是"变灰"的老式反馈，
  // 在白底上几乎看不出来（用户："按了之后没任何反应"）。现在再叠一层**跟手的缩放**：
  // 按压缩到 0.94、抬手带一点回弹，和全 App 其他按键同一套语言。
  val pressK by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
      label = "panelPillPress",
    )
  Box(
    modifier =
      Modifier.height(30.dp)
        .graphicsLayer {
          // 按下**放大**（用户口径：「往内缩不好看」）。全 App 统一这一条。
          val sc = 1f + 0.06f * pressK
          scaleX = sc
          scaleY = sc
        }
        .clip(RoundedCornerShape(15.dp))
        .background(LotusPalette.Ink.copy(alpha = if (pressed) 0.10f else if (emphasized) 0.07f else 0.04f))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
        .padding(horizontal = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (enabled) LotusPalette.Ink else LotusPalette.InkFaint,
      fontSize = 13.sp,
    )
  }
}

/** 合并面板里的一行会话动作。 */
@Composable
private fun PanelRow(text: String, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  // 按下：放大 + 一层薄折射。**不用灰色块**（用户口径：「就不要弄传统的操作逻辑了」）。
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "panelRowPress",
    )
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(38.dp)
        .pressLiquid(active = press, shape = RoundedCornerShape(10.dp))
        .clickable(interactionSource = interaction, indication = null) { onClick() }
        .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = text, color = LotusPalette.Ink, fontSize = 14.sp)
  }
}

/** 聊天页左上角的返回玻璃钮。**悬浮**，不占顶栏高度。 */
@Composable
fun FloatingBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  LiquidGlassIconButton(
    onClick = onClick,
    modifier = modifier,
    size = 42.dp,
    veil = 0.55f,
    blendAmbient = true,
    lensStrength = 1f,
  ) { tint ->
    LotusIcon(
      glyph = LotusGlyph.Back,
      tint = tint.copy(alpha = 0.72f),
      modifier = Modifier.size(19.dp),
    )
  }
}

/** 居中的玻璃确认框（删会话用）。跟 [GlassPromptDialog] 同一套语言，只是没有输入框。 */
@Composable
fun GlassConfirmDialog(
  title: String,
  body: String,
  confirmLabel: String,
  modifier: Modifier = Modifier,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val shape = RoundedCornerShape(26.dp)
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = ModalScrimAlpha))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { onDismiss() }
    )
    Column(
      modifier =
        Modifier.padding(horizontal = 40.dp)
          .fillMaxWidth()
          // 跟 [GlassPromptDialog] 完全同一块玻璃：统一配方 + 同样的 dim。
          // （原来这里是 0.85/白 0.22，改名框是 0.6/白 0.42 —— 两个模态弹层自己都不一样。）
          .floatGlass(
            shape = shape,
            strength = 0.7f,
            elevation = 24.dp,
            dim = ModalGlassDim,
          )
          .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
      Text(text = title, color = LotusPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(8.dp))
      Text(text = body, color = LotusPalette.InkMuted, fontSize = 13.sp)
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogButton(text = "取消", primary = false, modifier = Modifier.weight(1f)) { onDismiss() }
        DialogButton(text = confirmLabel, primary = true, modifier = Modifier.weight(1f)) {
          onConfirm()
        }
      }
    }
  }
}

/** 一闪而过的玻璃确认条：保存 / 归档完成后给一个非阻塞结果。 */
@Composable
fun GlassToast(text: String, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier =
      modifier
        .glassPanel(
          shape = shape,
          strength = 0.6f,
          veil = 1.8f,
          rimAlpha = 0.9f,
          elevation = 10.dp,
        )
        .padding(horizontal = 16.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LotusIcon(
      glyph = LotusGlyph.Sparkle,
      tint = LotusPalette.Ink.copy(alpha = 0.8f),
      modifier = Modifier.size(14.dp),
    )
    Spacer(Modifier.width(7.dp))
    Text(text = text, color = LotusPalette.Ink, fontSize = 13.sp)
  }
}

/**
 * 「+」的小菜单：**竖着排**的图片 / 视频 / 文件。
 *
 * 用户口径：「那加号出来的东西应该上下排呀，而且怎么还给那东西描边呢，太丑了，
 * 而且点进去之后没有扭曲那些 —— 每个玻璃点进去之后都应该会有扭曲感」。
 * 所以：一块玻璃壳（不带内部描边），里面三行，行按下时跟着折射放大（见 [AttachRow]）。
 */
@Composable
fun GlassAttachMenu(
  onPickImage: () -> Unit,
  onPickVideo: () -> Unit,
  onPickFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .width(132.dp)
        .floatGlass(shape = RoundedCornerShape(22.dp), strength = 0.5f, elevation = 12.dp)
        .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    AttachRow("图片", onPickImage)
    AttachRow("视频", onPickVideo)
    AttachRow("文件", onPickFile)
  }
}

/** 菜单里的一行。按下＝放大 + 一层薄折射（**不画边**）。 */
@Composable
private fun AttachRow(text: String, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "attachRowPress",
    )
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(40.dp)
        .pressLiquid(active = press, shape = RoundedCornerShape(12.dp))
        .clickable(interactionSource = interaction, indication = null) { onClick() }
        .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = text, color = LotusPalette.Ink, fontSize = 14.sp)
  }
}

/**
 * **它都有哪些工具** —— 一页列清楚。
 *
 * 用户口径：「1. 工具展示：显示它都有哪些工具。」「不是所有人都跟我用的一样，上游有些能用的
 * 用、有些不能用的就不用，但是这功能必须要有」。
 *
 * 所以这一页**不 promise 任何东西一定可用**：每一项都把自己的真实状态写出来 ——
 * 常开的（记忆 / 提示词）直接写"常开"，要看上游的写"看上游"，没配的写"未配置"。
 * 状态全部来自本机真实配置（[Capabilities] + 联网开关），不是写死的装饰。
 */
@Composable
fun GlassToolsPanel(
  webSearch: Boolean,
  searchReady: Boolean,
  imageReady: Boolean,
  videoReady: Boolean,
  ocrReady: Boolean,
  modifier: Modifier = Modifier,
  onDismiss: () -> Unit,
) {
  val shape = RoundedCornerShape(26.dp)
  val rows =
    listOf(
      ToolLine("记忆读取", "每轮请求都带上必读的人设与记忆（USER · MEMORY）", "常开"),
      ToolLine("记忆写入", "它能把要点写进 MEMORY.md；同一主题整条替换，不会越堆越长", "常开"),
      ToolLine("提示词文件", "它能改自己的人设 / 规则文件（SOUL · RULES）", "常开"),
      ToolLine("整理记忆", "它能把写乱的那一段重写整齐，而不是一路往下追加", "常开"),
      ToolLine(
        "联网搜索",
        if (webSearch) "请求里带 web_search 声明，检索在上游做；上游不回页面明细时，聊天里会如实写出来"
        else "已关闭。开了才能让模型自己上网",
        when {
          !webSearch -> "已关"
          else -> "看上游"
        },
      ),
      ToolLine(
        "搜索源",
        if (searchReady) "备用搜索服务已填（Tavily 这类固定接口）。自动调用还没接上，先只做备用"
        else "没配。模型自己不带联网时，这里是它的备用搜索服务",
        if (searchReady) "已配" else "未配置",
      ),
      ToolLine("生图", "配好接口 + key + 模型，就能在对话里直接出图", if (imageReady) "已配" else "未配置"),
      ToolLine("生视频", "文生视频 / 图生视频", if (videoReady) "已配" else "未配置"),
      ToolLine("解析器", "连视觉都没有的模型：先把图 / 文档里的字抠出来再喂给它", if (ocrReady) "已配" else "未配置"),
      ToolLine("视觉理解", "模型自己就能看图（发图前会先判断这一条支不支持）", "看上游"),
    )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = ModalScrimAlpha))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { onDismiss() }
    )
    Column(
      modifier =
        Modifier.padding(horizontal = 22.dp)
          .fillMaxWidth()
          .floatGlass(
            shape = shape,
            strength = 0.7f,
            elevation = 18.dp,
            dim = ModalGlassDim,
          )
          .padding(horizontal = 18.dp, vertical = 16.dp)
          .verticalScroll(rememberScrollState()),
    ) {
      Text("工具", color = LotusPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(2.dp))
      Text(
        "这些是它现在能用的全部能力。用什么、能不能用，取决于你配的提供方。",
        color = LotusPalette.InkMuted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
      )
      Spacer(Modifier.height(12.dp))
      rows.forEach { line ->
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
          Column(modifier = Modifier.weight(1f)) {
            Text(line.name, color = LotusPalette.Ink, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(line.detail, color = LotusPalette.InkMuted, fontSize = 10.5.sp, lineHeight = 15.sp)
          }
          Spacer(Modifier.width(10.dp))
          Text(
            text = line.state,
            color = LotusPalette.Ink.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
      }
      Spacer(Modifier.height(14.dp))
      DialogButton(text = "好", primary = true, modifier = Modifier.fillMaxWidth()) { onDismiss() }
    }
  }
}

private data class ToolLine(val name: String, val detail: String, val state: String)

/** 来源玻璃上的文字柔光：不靠白底板，也能压在背景纹理上读清。 */
private val SourceTextGlow =
  Shadow(color = Color.White.copy(alpha = 0.94f), offset = Offset.Zero, blurRadius = 8f)
private val SourceGlowStyle = TextStyle(shadow = SourceTextGlow)

/**
 * **来源列表** —— 点消息底下那枚站点图标弹出来的半屏。
 *
 * 用户口径（2026-10-17，对着 GPT 的截图）：「点开之后可以做成这样」。
 * 所以：不直接跳网页，先列清楚都用过哪些页面 —— 站点图标 / 站点名 / 标题 / 地址 / 日期；
 * 点其中一条才进 [ai.lotus.app.ui.reader.ReaderScreen]（应用内）。
 *
 * 标题是**真去抓页面 <title>**（抓不到就退回 URL 路径），不是编的。
 */
@Composable
fun GlassSourcesSheet(
  urls: List<String>,
  onOpen: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  val listState = rememberLazyListState()

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = 0.16f))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { onDismiss() }
    )
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .floatGlass(
            shape = shape,
            strength = 0.78f,
            elevation = 24.dp,
            veil = 1.08f,
            dim = ModalGlassDim,
          )
          .padding(horizontal = 18.dp)
          .padding(top = 10.dp, bottom = 18.dp)
    ) {
      Box(
        modifier =
          Modifier.align(Alignment.CenterHorizontally)
            .width(38.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LotusPalette.Ink.copy(alpha = 0.14f))
      )
      Spacer(Modifier.height(14.dp))
      Text(
        text = "来源",
        color = LotusPalette.Ink,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        style = SourceGlowStyle,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = "${urls.size} 个页面",
        color = LotusPalette.InkFaint,
        fontSize = 10.sp,
        style = SourceGlowStyle,
      )
      Spacer(Modifier.height(8.dp))
      LazyColumn(state = listState, modifier = Modifier.heightIn(max = 560.dp)) {
        items(urls) { url ->
          SourceSheetRow(url = url, onClick = { onOpen(url) })
          Box(
            Modifier.fillMaxWidth().height(0.7.dp)
              .background(LotusPalette.Ink.copy(alpha = 0.075f))
          )
        }
      }
    }
  }
}

@Composable
private fun SourceSheetRow(url: String, onClick: () -> Unit) {
  val domain = ai.lotus.app.ui.chat.hostOf(url)
  val favicon = ai.lotus.app.ui.chat.rememberFavicon(domain)
  val meta = rememberPageMeta(url)
  val press = rowPress(RoundedCornerShape(12.dp), scale = 1.012f, strength = 0.24f)
  val publisher = meta?.siteName?.takeIf { it.isNotBlank() } ?: domain

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .then(press.modifier)
        .clickable(interactionSource = press.interaction, indication = null, onClick = onClick)
        .padding(vertical = 13.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
      ) {
        if (favicon != null) {
          Image(
            bitmap = favicon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(21.dp).clip(RoundedCornerShape(5.dp)),
          )
        } else {
          Box(
            modifier =
              Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                .background(LotusPalette.Ink.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
          ) {
            LotusIcon(LotusGlyph.Link, LotusPalette.Ink.copy(alpha = 0.46f), Modifier.size(13.dp))
          }
        }
      }
      Spacer(Modifier.width(9.dp))
      Text(
        text = publisher,
        color = LotusPalette.InkMuted,
        fontSize = 10.5.sp,
        style = SourceGlowStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.height(7.dp))
    Text(
      text = meta?.title ?: pathOf(url),
      color = LotusPalette.Ink,
      fontSize = 14.5.sp,
      lineHeight = 20.sp,
      fontWeight = FontWeight.Medium,
      style = SourceGlowStyle,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    meta?.description?.takeIf { it.isNotBlank() }?.let { summary ->
      Spacer(Modifier.height(4.dp))
      Text(
        text = summary,
        color = LotusPalette.InkMuted,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        style = SourceGlowStyle,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
    meta?.published?.takeIf { it.isNotBlank() }?.let { date ->
      Spacer(Modifier.height(6.dp))
      Text(
        text = date,
        color = LotusPalette.InkFaint,
        fontSize = 9.5.sp,
        style = SourceGlowStyle,
        maxLines = 1,
      )
    }
  }
}

/** URL 的路径部分，作为拿不到标题时的兜底。 */
private fun pathOf(url: String): String =
  runCatching {
      val u = java.net.URI(url)
      val p = u.path.orEmpty().trimEnd('/')
      if (p.isBlank()) u.host ?: url else p
    }
    .getOrDefault(url)

private data class PageMeta(
  val title: String?,
  val description: String?,
  val published: String?,
  val siteName: String?,
)

/** 进程内缓存：同一个页面只抓一次标题 / 摘要 / 日期。 */
private val pageMetaCache = mutableMapOf<String, PageMeta?>()

/**
 * 只读页面头部 64KB，提取真实标题、摘要、发布时间和站点名。
 * 拿不到就返回 null / 空字段，绝不编内容。
 */
@Composable
private fun rememberPageMeta(url: String): PageMeta? {
  var meta by remember(url) { mutableStateOf(pageMetaCache[url]) }
  LaunchedEffect(url) {
    if (pageMetaCache.containsKey(url)) {
      meta = pageMetaCache[url]
      return@LaunchedEffect
    }
    val got =
      withContext(Dispatchers.IO) {
        runCatching {
            val conn =
              (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Lotus)")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
              }
            val html = conn.inputStream.use { it.bufferedReader().readText().take(65_536) }
            conn.disconnect()
            parsePageMeta(html)
          }
          .getOrNull()
      }
    pageMetaCache[url] = got
    meta = got
  }
  return meta
}

private fun parsePageMeta(html: String): PageMeta {
  val title =
    Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
      .find(html)
      ?.groupValues
      ?.get(1)
      ?.cleanHtml()
      ?.takeIf { it.isNotBlank() }

  val attrs =
    Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
      .findAll(html)
      .mapNotNull { match ->
        val tag = match.value
        val key =
          attrOf(tag, "property")
            ?: attrOf(tag, "name")
            ?: attrOf(tag, "itemprop")
        val content = attrOf(tag, "content")
        if (key.isNullOrBlank() || content.isNullOrBlank()) null else key.lowercase() to content.cleanHtml()
      }
      .toList()
      .toMap()

  val description =
    listOf("og:description", "description", "twitter:description")
      .firstNotNullOfOrNull { attrs[it]?.takeIf(String::isNotBlank) }

  val rawDate =
    listOf(
        "article:published_time",
        "datepublished",
        "date",
        "pubdate",
        "og:updated_time",
      )
      .firstNotNullOfOrNull { attrs[it]?.takeIf(String::isNotBlank) }
      ?: Regex("""["']datePublished["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(html)
        ?.groupValues
        ?.get(1)

  return PageMeta(
    title = title,
    description = description,
    published = prettyDate(rawDate),
    siteName = attrs["og:site_name"]?.takeIf { it.isNotBlank() },
  )
}

private fun attrOf(tag: String, name: String): String? =
  Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    .find(tag)
    ?.groupValues
    ?.get(1)
    ?.cleanHtml()

private fun String.cleanHtml(): String =
  replace(Regex("<[^>]+>"), " ")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun prettyDate(raw: String?): String? {
  if (raw.isNullOrBlank()) return null
  val head = raw.take(10)
  return runCatching {
      val date = java.time.LocalDate.parse(head)
      "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
    .getOrElse { raw.take(10).takeIf { it.matches(Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""")) } }
}
