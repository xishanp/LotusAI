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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
/** 长按一条消息时，动作条要挂在哪。坐标都相对 [ChatScreen] 的根 Box。 */
private data class MsgAnchor(val x: Float, val top: Float, val bottom: Float)

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
  /** 长按 → 引用：把原文插进输入框。 */
  onQuote: (String) -> Unit = {},
  /** 长按 → 编辑：把这条用户消息放回输入框、从这条起的历史丢掉。 */
  onEdit: (Long) -> Unit = {},
  /** 长按 → 重新回答（只对助手消息）。 */
  onRegenerate: (Long) -> Unit = {},
  /** 长按 → 撤回：从这条起往后删掉。 */
  onDelete: (Long) -> Unit = {},
  /** 正在生图的那条回复 id —— 那个气泡里会多一行"正在画图…"。 */
  paintingId: Long? = null,
) {
  val listState = rememberLazyListState()
  val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
  val density = LocalDensity.current

  // 根 Box 在窗口里的位置/尺寸：长按出来的动作条要按**气泡的实际位置**挂上去，
  // 用窗口坐标减根坐标换算成根内坐标，跟 LazyColumn 滚到哪儿都无关。
  val rootOrigin = remember { mutableStateOf(Offset.Zero) }
  val rootSize = remember { mutableStateOf(IntSize.Zero) }
  var target by remember { mutableStateOf<ChatMessage?>(null) }
  var anchor by remember { mutableStateOf<MsgAnchor?>(null) }
  var toast by remember { mutableStateOf<String?>(null) }

  // 新消息 / 模拟回复落地后，滚到最后一条（生图贴上来也要滚）。
  LaunchedEffect(messages.size, messages.lastOrNull()?.text, messages.lastOrNull()?.imagePath) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }
  LaunchedEffect(toast) {
    if (toast != null) {
      delay(1400)
      toast = null
    }
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .onGloballyPositioned {
          rootOrigin.value = it.positionInWindow()
          rootSize.value = it.size
        }
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
            onLongPress = { m, rect ->
              target = m
              anchor =
                MsgAnchor(
                  x = rect.left - rootOrigin.value.x,
                  top = rect.top - rootOrigin.value.y,
                  bottom = rect.bottom - rootOrigin.value.y,
                )
            },
          )
        }
      }
    }

    // 点空白 = 收掉动作条。跟"点空白收键盘"是同一个手势，别让菜单赖着不走。
    if (target != null) {
      Box(
        modifier =
          Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { target = null }
          }
      )
    }

    val t = target
    val a = anchor
    if (t != null && a != null) {
      // 动作条：4 枚 ── 复制 / 引用 / 编辑(或重答) / 撤回。宽度写死 236dp，位置按气泡算。
      val barW = 236.dp
      val barH = 46.dp
      val wPx = with(density) { barW.toPx() }
      val hPx = with(density) { barH.toPx() }
      val gapPx = with(density) { 8.dp.toPx() }
      val padPx = with(density) { 10.dp.toPx() }
      val floorPx = with(density) { (bottomInset + keyboardInset).toPx() } + gapPx
      val maxX = (rootSize.value.width - wPx - padPx).coerceAtLeast(padPx)
      val x = a.x.coerceIn(padPx, maxX)
      // 优先挂在气泡下面；下面放不下就翻到上面 —— 两头都不会掉出屏幕。
      var y = a.bottom + gapPx
      val bottomLimit = rootSize.value.height - floorPx - hPx
      if (y > bottomLimit) y = (a.top - hPx - gapPx).coerceAtLeast(padPx)
      MessageActionBar(
        message = t,
        modifier =
          Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(barW).height(barH),
        onCopy = {
          clipboard?.setPrimaryClip(ClipData.newPlainText("Lotus", t.text))
          toast = "已复制"
          target = null
        },
        onQuote = {
          onQuote(t.text)
          target = null
        },
        onEdit = {
          onEdit(t.id)
          target = null
        },
        onRegenerate = {
          onRegenerate(t.id)
          target = null
        },
        onDelete = {
          onDelete(t.id)
          target = null
        },
      )
    }

    toast?.let { msg ->
      Text(
        text = msg,
        color = LotusPalette.Ink,
        fontSize = 12.sp,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .padding(bottom = bottomInset + keyboardInset + 14.dp)
            .glassPanel(
              shape = RoundedCornerShape(20.dp),
              strength = 0.34f,
              rimAlpha = 0.5f,
              elevation = 8.dp,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
      )
    }
  }
}

/**
 * 长按气泡弹出的动作条。
 *
 * 它自己是一块玻璃（悬浮在页面之上，所以真的糊背景）；里面那 4 枚按键按铁律**静止隐形**，
 * 只有按下时用 [rowPress] 浮出一层薄折射 —— 同一块玻璃里不再长第二块玻璃。
 */
@Composable
private fun MessageActionBar(
  message: ChatMessage,
  modifier: Modifier = Modifier,
  onCopy: () -> Unit,
  onQuote: () -> Unit,
  onEdit: () -> Unit,
  onRegenerate: () -> Unit,
  onDelete: () -> Unit,
) {
  val shape = RoundedCornerShape(23.dp)
  Row(
    modifier =
      modifier
        .glassPanel(shape = shape, strength = 0.5f, rimAlpha = 0.6f, elevation = 18.dp)
        .padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ActionItem(LotusGlyph.Copy, "复制", onCopy, Modifier.weight(1f))
    ActionItem(LotusGlyph.Quote, "引用", onQuote, Modifier.weight(1f))
    if (message.fromUser) {
      ActionItem(LotusGlyph.Edit, "编辑", onEdit, Modifier.weight(1f))
    } else {
      ActionItem(LotusGlyph.Refresh, "重答", onRegenerate, Modifier.weight(1f))
    }
    ActionItem(LotusGlyph.Close, "撤回", onDelete, Modifier.weight(1f))
  }
}

@Composable
private fun ActionItem(
  glyph: LotusGlyph,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(16.dp)
  val press = rowPress(shape = shape, scale = 1.06f, strength = 0.4f)
  Column(
    modifier =
      modifier
        .fillMaxHeight()
        .then(press.modifier)
        .clickable(interactionSource = press.interaction, indication = null, onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    LotusIcon(
      glyph = glyph,
      tint = LotusPalette.Ink.copy(alpha = 0.82f),
      modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.height(3.dp))
    Text(text = label, color = LotusPalette.Ink.copy(alpha = 0.72f), fontSize = 9.sp)
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
  onLongPress: (ChatMessage, Rect) -> Unit = { _, _ -> },
) {
  // ⚠️ 四角**统一 20dp**（原来"朝头像那一角"收 6dp 做小尾巴，真机上读出来是个尖角，用户否掉了）。
  val shape = RoundedCornerShape(20.dp)
  // 长按 = 弹动作条。位置用气泡自己的窗口坐标算，滚动到哪儿都对得上。
  // ⚠️ 锚点取**整行**（气泡 + 头像 + 下面那行小字），不要取气泡本身：
  //    动作条挂在气泡正下方会正好压住"时间 · 字数"那行小字，玻璃底下透出半截笔画，
  //    看着就像一块残影。挂在整行下面 = 规规矩矩地排在小字下面。
  var rowBounds by remember { mutableStateOf(Rect.Zero) }
  val rowModifier =
    Modifier.fillMaxWidth().onGloballyPositioned { rowBounds = it.boundsInWindow() }
  val longPressModifier =
    Modifier.combinedClickable(
      onLongClick = { onLongPress(message, rowBounds) },
      onClick = {},
    )

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
        message.text.isNotBlank() -> MessageText(message.text)
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
      AssistantAvatar()
      Spacer(Modifier.height(6.dp))
      Box(
        modifier =
          Modifier.widthIn(max = BubbleMaxWidth)
            .then(longPressModifier)
            .glassPanel(shape = shape, strength = 0.45f, rimAlpha = 0.55f, elevation = 6.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        BubbleContent()
      }
      MessageMeta(message, display)
    }
    return
  }

  Column(modifier = rowModifier, horizontalAlignment = Alignment.End) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier =
          Modifier.widthIn(max = BubbleMaxWidth)
            .then(longPressModifier)
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
  }
}

/**
 * 气泡下面那行小字：模型 · 时间 · token · 字数。
 *
 * 哪几段出现完全由「设置 → 对话设置」的五个开关决定；**一段都没有时整行不占高度**
 * （否则每条消息底下都会多出一条空白）。
 */
@Composable
private fun MessageMeta(message: ChatMessage, display: ChatDisplay) {
  if (message.text.isEmpty()) return
  val parts =
    buildList {
      if (display.showModel && !message.fromUser && message.model.isNotBlank()) add(message.model)
      if (display.showTime && message.at > 0L) add(clockOf(message.at))
      if (display.showTokens && !message.fromUser && message.tokens > 0) add("${message.tokens} tok")
      if (display.showChars) add("${message.text.length} 字")
    }
  if (parts.isEmpty()) return
  Text(
    text = parts.joinToString(" · "),
    color = LotusPalette.InkFaint,
    fontSize = 10.sp,
    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
  )
}

/** 聊天里显示的时间：只给 HH:mm —— 日期在同一天里没有意义，反而占地方。 */
private fun clockOf(at: Long): String =
  runCatching {
      java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(at))
    }
    .getOrDefault("")

@Composable
private fun MessageText(text: String) {
  // 长按文字 = 系统那套"选取 / 复制 / 全选"。
  // 用户口径（2026-10-11）：「那个复制键，你得有东西选取啊 —— 选取、复制、全选这些」。
  // 之前只有气泡长按那一条"复制全文"，中间一截文字想单独复制根本选不中。
  SelectionContainer {
    Text(
      text = text,
      color = LotusPalette.Ink,
      fontSize = 15.sp,
      lineHeight = 22.sp,
    )
  }
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
private fun AssistantAvatar() {
  Image(
    painter = painterResource(R.drawable.lotus_thinking),
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier = Modifier.size(72.dp),
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
