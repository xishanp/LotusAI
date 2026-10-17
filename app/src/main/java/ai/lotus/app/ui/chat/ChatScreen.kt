package ai.lotus.app.ui.chat

import ai.lotus.app.R
import android.graphics.BitmapFactory
import ai.lotus.app.ui.glass.LiquidGlassIconButton
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.glass.rowPress
import ai.lotus.app.ui.glass.glassRim
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 一条消息。 */
data class ChatMessage(
  val id: Long,
  val fromUser: Boolean,
  val text: String,
  /** 落盘时间戳（毫秒）。0 = 这一条还没落盘。 */
  val at: Long = 0L,
  /** 这条是哪个模型答的（只有助手消息有）。 */
  val model: String = "",
  /** 这条答完时服务端报的 token 总量。0 = 没拿到。 */
  val tokens: Int = 0,
  /**
   * 用户这条消息带的图（本地绝对路径，见 [ai.lotus.app.data.Attachments]）。空 = 没图。
   * 图片本身是文件，**不进 conversations.json**，这里只存路径。
   */
  val imagePath: String = "",
  /** 上游给的推理过程（没有就是空 —— 界面上就不出现那一块）。 */
  val reasoning: String = "",
  /** 这一轮真正用到的能力（客户端记账，不是模型自称）。见 [ai.lotus.app.data.StoredMessage.tools]。 */
  val tools: List<String> = emptyList(),
  /** 上面那些动作的**明细**：`标签｜详情`。见 [ai.lotus.app.data.StoredMessage.toolLog]。 */
  val toolLog: List<String> = emptyList(),
  /**
   * **它真的读过哪些页面** —— 上游在这条回答里回的 `annotations`（URL + 标题）。
   *
   * ⚠️ 这跟"正文里写了几个链接"是两回事：正文里的链接是模型自己写的，可能编；
   *    这个是上游检索通道报回来的。**上游不给就老实空着**，不许拿正文链接冒充访问记录。
   */
  val sources: List<String> = emptyList(),
)

/**
 * 聊天页要显示哪些附加信息。
 *
 * 全部来自「设置 → 对话设置」那五个开关。默认只开模型/头像/时间 —— 后两项（token、字数）
 * 是给自己算账用的，默认关着，免得每条消息下面都挂一串数字。
 */
data class ChatDisplay(
  val showModel: Boolean = true,
  val showUserAvatar: Boolean = true,
  val showTime: Boolean = true,
  val showTokens: Boolean = false,
  val showChars: Boolean = false,
)

/** 气泡最大宽度。留出两侧头像 + 边距，超过就该换行了。 */
private val BubbleMaxWidth = 288.dp

/**
 * 聊天页（模拟）。
 *
 * 版式跟微信一致：**两边的头像 + 各自一侧的气泡**
 *  - AI：立绘头像在左，气泡靠左；
 *  - 用户：头像在右，气泡靠右。
 *
 * 气泡全是液态玻璃（`glassPanel`）：霜面 + 描边 + 折射，白底上靠**顶白下灰的边**立住。
 * 气泡的"尾巴"不用尖角，直接把朝头像那个角收圆到 6dp —— 苹果风的做法，也不会在玻璃上切出硬角。
 *
 * ⚠️ 气泡玻璃**不许**加 `blendAmbient` 那种自采样：它就在 page 层里，采样自己 = 残影。
 *    这里 `glassPanel` 默认拿 page 层的 local backdrop（纯色底），是安全的。
 *
 * @param bottomInset 底部固定层（输入条 + 底栏）占掉的高度，列表据此留出滚动空间。
 * @param keyboardInset 键盘当前高度。输入条会跟着键盘抬，列表也必须多留这么多，
 *   否则最后几条会被键盘盖住 —— 而且是"滚到底也看不见"那种。
 */
@Composable
fun ChatScreen(
  messages: List<ChatMessage>,
  bottomInset: Dp,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  keyboardInset: Dp = 0.dp,
  display: ChatDisplay = ChatDisplay(),
  /** 用户自己传的头像文件路径（见 [ai.lotus.app.data.AvatarStore]）。null = 还没传。 */
  userAvatarPath: String? = null,
  /** 消息底部动作 → 引用：把原文插进输入框。 */
  onQuote: (String) -> Unit = {},
  /** 消息底部动作 → 编辑：把这条用户消息放回输入框、从这条起的历史丢掉。 */
  onEdit: (Long) -> Unit = {},
  /** 消息底部动作 → 重新回答（只对助手消息）。 */
  onRegenerate: (Long) -> Unit = {},
  /** 消息底部动作 → 撤回：从这条起往后删掉。 */
  onDelete: (Long) -> Unit = {},
  /** 正在生图的那条回复 id —— 那个气泡里会多一行"正在画图…"。 */
  paintingId: Long? = null,
  /** 正在流式回答的那条消息 id。它决定"思考中"玻璃面板自动展开、答完自动收起。 */
  replyingId: Long? = null,
  /** 点消息底部的「来源」→ 弹出这一条的全部来源（应用内），不跳浏览器。 */
  onOpenSources: (List<String>) -> Unit = {},
) {
  val listState = rememberLazyListState()
  val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)

  // 新消息 / 模拟回复落地后，滚到最后一条（生图贴上来也要滚）。
  LaunchedEffect(messages.size, messages.lastOrNull()?.text, messages.lastOrNull()?.imagePath) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }
  Box(
    modifier = modifier.fillMaxSize()
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // ⚠️ 这一页**没有顶栏**：返回钮 / 更多钮由 LotusApp 的悬浮玻璃层画，压在最上面。
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding =
          PaddingValues(
            start = 16.dp,
            end = 16.dp,
            // 顶部留出悬浮钮的高度（保住第一屏观感，消息一多照样从玻璃底下滑过去）。
            top = 76.dp,
            bottom = bottomInset + keyboardInset + 12.dp,
          ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(messages, key = { it.id }) { message ->
          MessageRow(
            message = message,
            display = display,
            userAvatarPath = userAvatarPath,
            painting = message.id == paintingId,
            streaming = message.id == replyingId,
            onQuote = onQuote,
            onEdit = onEdit,
            onRegenerate = onRegenerate,
            onDelete = onDelete,
            onOpenSources = onOpenSources,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
  message: ChatMessage,
  display: ChatDisplay = ChatDisplay(),
  userAvatarPath: String? = null,
  /** 这条正在生图 —— 气泡里显示"正在画图…"＋跳动的小点。 */
  painting: Boolean = false,
  /** 这条正在流式回答：过程玻璃自动打开，正文为空时不另画三个点。 */
  streaming: Boolean = false,
  onQuote: (String) -> Unit = {},
  onEdit: (Long) -> Unit = {},
  onRegenerate: (Long) -> Unit = {},
  onDelete: (Long) -> Unit = {},
  onOpenSources: (List<String>) -> Unit = {},
) {
  // ⚠️ 四角**统一 20dp**（原来"朝头像那一角"收 6dp 做小尾巴，真机上读出来是个尖角，用户否掉了）。
  val shape = RoundedCornerShape(20.dp)
  val rowContext = LocalContext.current
  val rowModifier = Modifier.fillMaxWidth()
  val bubbleInteraction = remember { MutableInteractionSource() }
  val bubblePressed by bubbleInteraction.collectIsPressedAsState()
  val bubblePress by
    animateFloatAsState(
      targetValue = if (bubblePressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
      label = "bubblePress",
    )
  val bubbleLift =
    Modifier.graphicsLayer {
      val k = 1f + 0.015f * bubblePress
      scaleX = k
      scaleY = k
    }
  val bubbleFeedback =
    bubbleLift.clickable(
      interactionSource = bubbleInteraction,
      indication = null,
      onClick = {},
    )

  // 过程面板：默认完全不存在，不占位。
  //  · 流式回答时自动出现，实时展示思考 / 工具；
  //  · 回答结束后自动收起；
  //  · 之后只有点 AI 头像才会显示。
  val hasProcessData =
    message.reasoning.isNotBlank() || message.toolLog.isNotEmpty() || message.tools.isNotEmpty()
  var processOpen by remember(message.id) { mutableStateOf(streaming) }
  LaunchedEffect(streaming) { processOpen = streaming }
  val toggleProcess = { processOpen = !processOpen }

  /** 气泡里那块内容：图（用户发的 / 模型生成的）+ 正文。 */
  @Composable
  fun BubbleContent() {
    Column {
      if (message.imagePath.isNotBlank()) {
        val shot = rememberFileBitmap(message.imagePath)
        // 图第一次出现 → 0→1，配合 graphicsLayer + blur 做"从模糊收到清晰"。
        var landed by remember(message.imagePath) { mutableStateOf(false) }
        LaunchedEffect(message.imagePath) { landed = true }
        val land by
          androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (landed) 1f else 0f,
            animationSpec = tween(420),
            label = "imageLand",
          )
        if (shot != null) {
          Image(
            bitmap = shot,
            contentDescription = if (message.fromUser) "发出的图片" else "生成的图片",
            contentScale = ContentScale.Fit,
            modifier =
              Modifier.widthIn(max = 232.dp)
                .heightIn(max = 300.dp)
                .graphicsLayer {
                  // 占位玻璃 → 真图：从「模糊 + 略大」收到「清晰 + 原位」。
                  scaleX = 1f + 0.06f * (1f - land)
                  scaleY = 1f + 0.06f * (1f - land)
                  alpha = 0.25f + 0.75f * land
                }
                .clip(RoundedCornerShape(14.dp)),
          )
        }
        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
      }
      when {
        message.text.isNotBlank() -> MessageText(message)
        message.imagePath.isBlank() && !painting -> TypingDots()
      }
      if (painting) {
        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
        PaintingPlaceholder()
      }
    }
  }

  // ⚠️ AI 那侧是**竖排**：立绘在上面、气泡在它下面（用户口径：「气泡放在角色头像的下方吧」）。
  //    用户那侧维持横排（文字 + 右侧头像）。
  if (!message.fromUser) {
    Column(modifier = rowModifier, horizontalAlignment = Alignment.Start) {
      AssistantAvatar(onClick = toggleProcess)
      if (processOpen) {
        Spacer(Modifier.height(6.dp))
        ProcessBlock(
          message = message,
          streaming = streaming,
          emptyHint = !hasProcessData,
          onToggle = toggleProcess,
        )
      }
      // 流式阶段正文没出来时，过程玻璃就是主体；不要再额外挂一个空白气泡。
      val showBubble =
        message.text.isNotBlank() ||
          message.imagePath.isNotBlank() ||
          painting ||
          !streaming ||
          !processOpen
      if (showBubble) {
        Spacer(Modifier.height(6.dp))
        Box(
          modifier =
            Modifier.widthIn(max = BubbleMaxWidth)
              .then(bubbleFeedback)
              .glassPanel(shape = shape, strength = 0.45f, rimAlpha = 0.55f, elevation = 6.dp)
              .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          BubbleContent()
        }
      }
      MessageMeta(message, display)
      MessageFooter(
        message = message,
        onCopy = {
          val cm = rowContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText("Lotus", message.text))
        },
        onQuote = { onQuote(message.text) },
        onRegenerate = { onRegenerate(message.id) },
        onDelete = { onDelete(message.id) },
        onOpenSources = onOpenSources,
      )
    }
    return
  }

  Column(modifier = rowModifier, horizontalAlignment = Alignment.End) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier =
          Modifier.widthIn(max = BubbleMaxWidth)
            .then(bubbleFeedback)
            .glassPanel(shape = shape, strength = 0.45f, rimAlpha = 0.55f, elevation = 6.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        BubbleContent()
      }
      if (display.showUserAvatar) {
        Spacer(Modifier.width(8.dp))
        UserAvatar(userAvatarPath)
      }
    }
    MessageMeta(message, display)
    MessageFooter(
      message = message,
      onCopy = {
        val cm = rowContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Lotus", message.text))
      },
      onQuote = { onQuote(message.text) },
      onEdit = { onEdit(message.id) },
      onRegenerate = { onRegenerate(message.id) },
      onDelete = { onDelete(message.id) },
      onOpenSources = onOpenSources,
      alignEnd = true,
    )
  }
}

/**
 * 「这一轮它做了什么」—— 思考过程 + 工具动作。
 *
 * 用户口径：「如果你直接拿那堆摆在那 太丑了 你应该有个详细的 或者有个能点开的东西
 * 能看它都操作了什么」。
 *
 * 所以：**默认只留一行极淡的摘要**（`思考 · 记忆 · 生图`），不占视觉重量；
 * 点这一行、或者点上面的人物头像，才展开明细。生成中自动展开，落地后自动收起。
 *
 * 材质：这是消息旁边的一行注，**不是一块玻璃**，也不是一张卡片 ——
 * 收起时只有字；展开时用一条发丝竖线把明细拢起来（铁律：同一处不许出现第二块玻璃）。
 */
@Composable
private fun ProcessBlock(
  message: ChatMessage,
  streaming: Boolean,
  emptyHint: Boolean,
  onToggle: () -> Unit,
) {
  if (message.fromUser) return
  val hasThink = message.reasoning.isNotBlank()
  val actions =
    when {
      message.toolLog.isNotEmpty() -> message.toolLog
      message.tools.isNotEmpty() -> message.tools.map { it + "｜" }
      else -> emptyList()
    }
  val shape = RoundedCornerShape(18.dp)
  val press = rowPress(shape = RoundedCornerShape(10.dp), scale = 1.02f, strength = 0.24f)

  Column(
    modifier =
      Modifier.widthIn(max = BubbleMaxWidth)
        .glassPanel(
          shape = shape,
          strength = 0.52f,
          rimAlpha = 0.58f,
          elevation = 5.dp,
        )
        .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.clip(RoundedCornerShape(9.dp))
          .then(press.modifier)
          .clickable(
            interactionSource = press.interaction,
            indication = null,
            onClick = onToggle,
          )
          .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
      LotusIcon(
        glyph = LotusGlyph.Sparkle,
        tint = LotusPalette.Ink.copy(alpha = 0.62f),
        modifier = Modifier.size(12.dp),
      )
      Spacer(Modifier.width(5.dp))
      Text(
        text = if (streaming) "正在思考…" else "思考过程",
        color = LotusPalette.Ink.copy(alpha = 0.70f),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
      )
      if (actions.isNotEmpty()) {
        Spacer(Modifier.width(6.dp))
        Text(
          text = "${actions.size} 项工具记录",
          color = LotusPalette.Ink.copy(alpha = 0.44f),
          fontSize = 10.sp,
        )
      }
      Spacer(Modifier.width(4.dp))
      LotusIcon(
        glyph = LotusGlyph.Chevron,
        tint = LotusPalette.Ink.copy(alpha = 0.46f),
        modifier = Modifier.size(9.dp).graphicsLayer { rotationZ = -90f },
      )
    }

    Column(
      modifier =
        Modifier.heightIn(max = 260.dp)
          .verticalScroll(rememberScrollState())
          .padding(top = 7.dp)
    ) {
      when {
        hasThink -> {
          Text(
            text = message.reasoning.trim(),
            color = LotusPalette.Ink.copy(alpha = 0.70f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
          )
        }
        streaming -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "正在等待模型的思考内容",
              color = LotusPalette.Ink.copy(alpha = 0.50f),
              fontSize = 11.sp,
            )
            Spacer(Modifier.width(8.dp))
            TypingDots()
          }
        }
        emptyHint -> {
          Text(
            text = "该模型没有返回思考过程，也没有触发工具。",
            color = LotusPalette.Ink.copy(alpha = 0.46f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
          )
        }
      }

      if (actions.isNotEmpty()) {
        if (hasThink || streaming || emptyHint) Spacer(Modifier.height(9.dp))
        Text(
          text = "工具记录",
          color = LotusPalette.Ink.copy(alpha = 0.44f),
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        actions.forEach { entry ->
          val label = entry.substringBefore('｜')
          val detail = entry.substringAfter('｜', "")
          Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = if (entry != actions.first()) 6.dp else 0.dp),
          ) {
            LotusIcon(
              glyph = toolGlyph(label),
              tint = LotusPalette.Ink.copy(alpha = 0.54f),
              modifier = Modifier.padding(top = 1.dp).size(11.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = label,
              color = LotusPalette.Ink.copy(alpha = 0.70f),
              fontSize = 11.sp,
              lineHeight = 15.sp,
              modifier = Modifier.alignByBaseline(),
            )
            if (detail.isNotBlank()) {
              Spacer(Modifier.width(6.dp))
              Text(
                text = detail,
                color = LotusPalette.Ink.copy(alpha = 0.48f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline(),
              )
            }
          }
        }
      }
    }
  }
}

private fun toolGlyph(label: String): LotusGlyph =
  when (label) {
    "联网" -> LotusGlyph.Globe
    "生图" -> LotusGlyph.Sparkle
    "提示词" -> LotusGlyph.Doc
    "记住", "记忆", "用户记忆", "长期记忆" -> LotusGlyph.Doc
    "整理", "归档" -> LotusGlyph.Folder
    else -> LotusGlyph.Clock
  }

/**
 * 气泡下面那行小字：模型 · 时间 · token · 字数。
 *
 * 哪几段出现完全由「设置 → 对话设置」的五个开关决定；**一段都没有时整行不占高度**
 * （否则每条消息底下都会多出一条空白）。
 */
@Composable
private fun MessageMeta(message: ChatMessage, display: ChatDisplay) {
  val parts =
    buildList {
      if (display.showModel && !message.fromUser && message.model.isNotBlank()) add(message.model)
      if (display.showTime && message.at > 0L) add(clockOf(message.at))
      if (display.showTokens && !message.fromUser && message.tokens > 0) add("${message.tokens} tok")
      if (display.showChars && message.text.isNotEmpty()) add("${message.text.length} 字")
    }
  if (parts.isEmpty()) return
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
  ) {
    if (parts.isNotEmpty()) {
      Text(
        text = parts.joinToString(" · "),
        color = LotusPalette.InkFaint,
        fontSize = 10.sp,
      )
      Spacer(Modifier.width(6.dp))
    }
  }
}

/**
 * 从正文里抠 http(s) 链接的正则。
 *
 * 单独拎成顶层常量：一是**只编译一次**（以前写在函数里，每帧过一眼都要 new 一个 Regex），
 * 二是让「这条正则到底编不编得过」有个固定落点 —— 2026-10-17 就是它把 App 崩过一次。
 *
 * 逻辑上做过验证（JDK 实跑）：`见 https://a.com/x 和 (https://b.com)。` → 两句都能抠出来，
 * 且不会把中文句号和右括号吃进 URL 尾巴。
 */
private val UrlPattern = Regex("https?://[^\\s)\\]}>\"“”'，。；]+")

/** 从正文里抠 http(s) 链接。尾巴上的标点要剪掉，不然复制出来的地址点不开。 */
private fun extractUrls(text: String): List<String> =
  // ⚠️ 之前这里手滑多写了一个收尾的 `)`，正则编译直接抛 PatternSyntaxException ——
  //    消息一进列表就把整个 App 崩回桌面（2026-10-17 实测崩过）。改完先跑一遍编译 + 上机。
  UrlPattern
    .findAll(text)
    .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', '、', '。', '，', '；', '！', '？') }
    .filter { it.length > 8 }
    .distinct()
    .take(8)
    .toList()

/** 链接显示成 `example.com` —— 一条 URL 全文占地方，读起来也不认得。 */
internal fun hostOf(url: String): String =
  runCatching { java.net.URI(url).host?.removePrefix("www.") }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
    ?: url.take(30)

/** 聊天里显示的时间：只给 HH:mm —— 日期在同一天里没有意义，反而占地方。 */
private fun clockOf(at: Long): String =
  runCatching {
      java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(at))
    }
    .getOrDefault("")

@Composable
private fun MessageText(message: ChatMessage) = PlainText(message.text)

@Composable
private fun PlainText(text: String) {
  // 长按文字 = 系统那套"选取 / 复制 / 全选"（用户 2026-10-11 点名要的）。
  SelectionContainer {
    Text(
      text = text,
      color = LotusPalette.Ink,
      fontSize = 15.sp,
      lineHeight = 22.sp,
    )
  }
}

/**
 * 每条回答**最下面那一行** —— 照 GPT 的排版：
 *
 *  - **左边**：一串纯图标动作（复制 / 引用 / 重答 / 撤回），不带文字、浅灰、等高间距；
 *  - **右边**：「来源」两个字 + 一排站点小图标，点开走**应用内阅读器**。
 *
 * 用户口径（2026-10-17）：「放最左边」「你那几个按钮重新弄一下 好丑」
 * 「以后如果有来源的话 再把来源放在那个地方 位置放得更靠右一点 跟 GPT 对齐」。
 */
@Composable
private fun MessageFooter(
  message: ChatMessage,
  onCopy: () -> Unit,
  onQuote: () -> Unit,
  onEdit: () -> Unit = {},
  onRegenerate: () -> Unit,
  onDelete: () -> Unit,
  onOpenSources: (List<String>) -> Unit,
  alignEnd: Boolean = false,
) {
  val sources =
    remember(message.id, message.sources, message.text) {
      (message.sources.filter { it.isNotBlank() } + extractUrls(message.text))
        .distinct()
        .take(6)
    }
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FooterIcon(LotusGlyph.Copy, onCopy)
    FooterIcon(LotusGlyph.Quote, onQuote)
    FooterIcon(
      if (message.fromUser) LotusGlyph.Edit else LotusGlyph.Refresh,
      if (message.fromUser) onEdit else onRegenerate,
    )
    FooterIcon(LotusGlyph.Close, onDelete)
    if (sources.isNotEmpty()) {
      Spacer(Modifier.width(2.dp))
      // 「来源」+ 站点小图标是一整组：贴在动作右边；点文字或任一图标都打开来源列表。
      val sourcePress = rowPress(RoundedCornerShape(9.dp), scale = 1.04f, strength = 0.22f)
      Row(
        modifier =
          Modifier.height(30.dp)
            .then(sourcePress.modifier)
            .clip(RoundedCornerShape(9.dp))
            .clickable(
              interactionSource = sourcePress.interaction,
              indication = null,
            ) { onOpenSources(sources) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "来源",
          color = LotusPalette.Ink.copy(alpha = 0.46f),
          fontSize = 10.sp,
          modifier = Modifier.padding(end = 3.dp),
        )
        sources.take(3).forEach { url ->
          FaviconDot(url)
          Spacer(Modifier.width(4.dp))
        }
      }
    }
  }
}

/**
 * 底部动作：**只有图标**（照 GPT）。
 *
 * 之前是「图标 + 两三个汉字」，一排四个在窄屏上挤成一坨 —— 用户否掉的就是那个。
 * 点击区仍然给到 30dp，眼睛看着是 15dp 一个淡淡的小图标。
 */
@Composable
private fun FooterIcon(glyph: LotusGlyph, onClick: () -> Unit) {
  val press = rowPress(shape = RoundedCornerShape(8.dp), scale = 1.14f, strength = 0.28f)
  Box(
    modifier =
      Modifier.size(30.dp)
        .then(press.modifier)
        .clip(RoundedCornerShape(8.dp))
        .clickable(interactionSource = press.interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    LotusIcon(glyph, LotusPalette.Ink.copy(alpha = 0.48f), Modifier.size(15.dp))
  }
}

/** 来源：只显示站点小图标（16dp），点开是应用内阅读器。抓不到 favicon 就退回一个链接小图标。 */
@Composable
private fun FaviconDot(url: String) {
  val domain = hostOf(url)
  val favicon = rememberFavicon(domain)
  Box(
    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (favicon != null) {
      Image(
        bitmap = favicon,
        contentDescription = domain,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)),
      )
    } else {
      LotusIcon(LotusGlyph.Link, LotusPalette.Ink.copy(alpha = 0.46f), Modifier.size(13.dp))
    }
  }
}

/**
 * 抓并缓存一个站点的 favicon（`https://<域名>/favicon.ico`）。
 *
 * ⚠️ 不用 Google / DuckDuckGo 那种图标代理：那两家在国内网络下经常打不开，
 *    我们自己直连站点反而稳。抓不到就返回 null，UI 退化成一个小链接图标，不报错、不重试轰炸。
 *    内存 + 磁盘各一层缓存，翻历史消息不会每次重新下载。
 */
private val faviconMemory = mutableMapOf<String, ImageBitmap?>()

@Composable
internal fun rememberFavicon(domain: String): ImageBitmap? {
  val context = LocalContext.current
  var bitmap by remember(domain) { mutableStateOf(faviconMemory[domain]) }
  LaunchedEffect(domain) {
    if (faviconMemory.containsKey(domain)) {
      bitmap = faviconMemory[domain]
      return@LaunchedEffect
    }
    val loaded =
      withContext(Dispatchers.IO) {
        runCatching {
            val file = File(File(context.filesDir, "favicons"), "$domain.png")
            if (file.exists()) {
              BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else {
              val conn =
                (URL("https://$domain/favicon.ico").openConnection() as HttpURLConnection).apply {
                  connectTimeout = 4000
                  readTimeout = 4000
                  setRequestProperty("User-Agent", "Mozilla/5.0 (Lotus)")
                }
              val raw = conn.inputStream.use { it.readBytes() }
              conn.disconnect()
              // 先落盘再解码：下次进 App 不用再联网。
              file.parentFile?.mkdirs()
              file.writeBytes(raw)
              BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
            }
          }
          .getOrNull()
      }
    faviconMemory[domain] = loaded
    bitmap = loaded
  }
  return bitmap
}

/** 等模型回来的三点。纯装饰，接上真模型后可用消息状态替换。 */
@Composable
private fun TypingDots() {
  val transition = rememberInfiniteTransition(label = "typing")
  Row(verticalAlignment = Alignment.CenterVertically) {
    repeat(3) { i ->
      val alpha by
        transition.animateFloat(
          initialValue = 0.25f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 520, delayMillis = i * 130),
              repeatMode = RepeatMode.Reverse,
            ),
          label = "dot$i",
        )
      Box(
        modifier =
          Modifier.padding(horizontal = 2.dp)
            .size(6.dp)
            .clip(CircleShape)
            .background(LotusPalette.Ink.copy(alpha = alpha)),
      )
    }
  }
}

/** 本地文件 → ImageBitmap（IO 线程解码，失败 = null，绝不让一张坏图把聊天页顶崩）。 */
@Composable
private fun rememberFileBitmap(path: String): ImageBitmap? {
  var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(path) {
    bitmap =
      withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
      }
  }
  return bitmap
}

/**
 * AI 头像：**整张立绘，不裁圆、不套圈**（用户口径：「就不要有一个圈圈着了，就一个完整的」）。
 *
 * ⚠️ 这里用的**不是**首页那张「双手托脸」(`lotus_mascot`)，而是专门为聊天生成的那张
 * **「一只手托着下巴摩挲 + 头顶问号」(`lotus_thinking`)**。
 * 用户口径：「聊天要用到的是这个带问号的呀…那你生成这个的意义是啥？」
 * —— 首页 = lotus_mascot；聊天 = lotus_thinking，两张各司其职。
 *
 * - 尺寸 72dp：比单行气泡（≈40dp）**明显大一点**（用户两次说"太小了"）。
 * - `ContentScale.Fit`：整张按比例缩，绝不裁掉呆毛、莲花和头顶的问号。
 * - 白底立绘 + 纯白画布 ⇒ 不要底板、不要描边；加圈反而变回图标。
 */
@Composable
private fun AssistantAvatar(onClick: (() -> Unit)? = null) {
  // 点人物 = 展开/收起「这一轮它做了什么」（用户口径：「是点人物让它出现这些吗」）。
  // 没东西可看时（onClick == null）保持纯展示，不做无效的点击反馈。
  if (onClick == null) {
    Image(
      painter = painterResource(R.drawable.lotus_thinking),
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = Modifier.size(72.dp),
    )
    return
  }
  // 反馈：原地鼓起来。不用水波、不变灰（同一套液态玻璃口径）。
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val k by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
      label = "avatarPress",
    )
  Image(
    painter = painterResource(R.drawable.lotus_thinking),
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier =
      Modifier.size(72.dp)
        .graphicsLayer {
          val f = 1f + 0.06f * k
          scaleX = f
          scaleY = f
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick),
  )
}

/**
 * 用户头像。
 *
 * 用户口径：「对话聊天那还得加一个上传用户头像」。
 * - 传过 → 直接画那张图，裁成圆的（用户自己的照片，裁圆是相册头像的通行做法）。
 * - 没传 → 一枚玻璃圆片 + 人像图标（跟以前一样）。
 *
 * ⚠️ 解码放在 `remember(path)` 里：只在那张图变了的时候读盘，不是每帧。
 */
@Composable
private fun UserAvatar(avatarPath: String?) {
  val bitmap =
    remember(avatarPath) {
      avatarPath?.let { p -> runCatching { BitmapFactory.decodeFile(p) }.getOrNull() }
    }
  if (bitmap != null) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(36.dp).clip(CircleShape),
    )
    return
  }
  Box(
    modifier =
      Modifier.size(36.dp)
        .clip(CircleShape)
        .glassPanel(shape = CircleShape, strength = 0.4f, rimAlpha = 0.5f, elevation = 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    LotusIcon(
      glyph = LotusGlyph.Person,
      tint = LotusPalette.Ink.copy(alpha = 0.6f),
      modifier = Modifier.size(19.dp),
    )
  }
}

/**
 * 生图 / 生视频的占位：一块淡玻璃 + **一片墨色颗粒在快速乱蹦**。
 *
 * 用户口径（2026-10-11）：「这个 UI 差点意思…有非常多个颗粒黑色的在快速跃动、
 * 无规则地动、起起伏伏」。
 *
 * ## 怎么动
 *
 * 每颗粒子有自己的相位 / 速度 / 振幅 / 粗细 / 漂移；纵向用两条**不可通约**频率的
 * `|sin|` 叠起来（用 `|sin|` 而不是 `sin`，才有"砸到地上再弹起来"的顿挫感），
 * 横向再叠一点慢漂移 —— 于是整片是"起起伏伏"的，而不是一排整齐的波浪。
 *
 * 时间由 **frame 驱动**的 state 累加，并且**只在绘制阶段读**：每帧只重画，不重组、不重排。
 */
@Composable
private fun PaintingPlaceholder() {
  // 每颗粒子的随机参数只算一次。种子写死 ⇒ 每次进来都是同一片，不会看着眼晕。
  val parts = remember {
    val r = kotlin.random.Random(20261011)
    List(PaintingParticleCount) {
      PaintingParticle(
        x = 0.03f + r.nextFloat() * 0.94f,
        phase = r.nextFloat(),
        speed = 1.10f + r.nextFloat() * 2.30f,
        amp = 0.30f + r.nextFloat() * 0.70f,
        radius = 0.9f + r.nextFloat() * 1.5f,
        alpha = 0.30f + r.nextFloat() * 0.62f,
        drift = 0.40f + r.nextFloat() * 1.40f,
      )
    }
  }
  var t by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(Unit) {
    var last = 0L
    while (true) {
      withFrameMillis { now ->
        // 夹一下间隔：帧掉到 50ms 以上时别让粒子瞬移一大截（看起来像"闪"）。
        if (last != 0L) t += ((now - last).coerceAtMost(50L)) / 1000f
        last = now
      }
    }
  }
  Box(
    modifier =
      Modifier.size(196.dp)
        .glassPanel(
          shape = RoundedCornerShape(16.dp),
          strength = 0.5f,
          tint = Color.White.copy(alpha = 0.26f),
          rimAlpha = 0.55f,
          elevation = 8.dp,
        )
        .clip(RoundedCornerShape(16.dp)),
  ) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val band = h * 0.66f
      parts.forEach { p ->
        val f1 = abs(sin((t * p.speed + p.phase) * PaintingPi))
        val f2 = abs(sin((t * p.speed * 0.47f + p.phase * 2.3f) * PaintingPi))
        val rise = (0.58f * f1 + 0.42f * f2) * p.amp
        val y = h * 0.86f - band * rise
        val x = w * p.x + sin(t * p.drift + p.phase * 7f) * w * 0.022f
        drawCircle(
          color = LotusPalette.Ink.copy(alpha = p.alpha),
          radius = p.radius.dp.toPx(),
          center = Offset(x, y),
        )
      }
    }
  }
}

/** 颗粒数量。用户要"非常多"，1080p 上 90 颗已经是一片了，再多只会糊成一块灰。 */
private const val PaintingParticleCount = 90

private const val PaintingPi = 3.1415927f

private class PaintingParticle(
  val x: Float,
  val phase: Float,
  val speed: Float,
  val amp: Float,
  val radius: Float,
  val alpha: Float,
  val drift: Float,
)
