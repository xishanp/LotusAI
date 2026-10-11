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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
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
        .floatGlass(shape = shape, strength = 0.55f, elevation = 14.dp)
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
          modifier = Modifier.fillMaxWidth(),
        )
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
              scaleX = 1f - 0.03f * k
              scaleY = 1f - 0.03f * k
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
 * 聊天页右上角**一块**面板：上面是用量与压缩/裁剪，下面是会话动作。
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
  keepRecent: Int,
  summaryChars: Int,
  busy: Boolean,
  /** 「记住这段」正在跑（要一次模型调用，得让用户看得见）。 */
  rememberBusy: Boolean = false,
  /** 上一次「记住这段」的结果，写在行尾。 */
  rememberHint: String = "",
  modifier: Modifier = Modifier,
  onCompress: () -> Unit,
  onTrim: (Int) -> Unit,
  onRemember: () -> Unit = {},
  /** 更新日志（更多里的一项）。 */
  onChangelog: () -> Unit = {},
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
        .floatGlass(shape = shape, strength = 0.55f, elevation = 14.dp)
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
          if (keepRecent > 0) append(" · 只发最近 $keepRecent 条")
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
        text = if (keepRecent > 0) "取消裁剪" else "裁剪",
        enabled = !busy,
        emphasized = false,
        onClick = { onTrim(if (keepRecent > 0) 0 else 20) },
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
    PanelRow("开新聊天", onClick = onNewChat)
    PanelRow("修改聊天名称", onClick = onRename)
    PanelRow("删除这个会话", onClick = onDelete)
  }
}

/** 合并面板里的小胶囊动作（压缩 / 裁剪）。 */
@Composable
private fun PanelPill(
  text: String,
  enabled: Boolean,
  emphasized: Boolean,
  onClick: () -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Box(
    modifier =
      Modifier.height(30.dp)
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
