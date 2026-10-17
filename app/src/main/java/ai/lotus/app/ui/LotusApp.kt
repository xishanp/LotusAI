package ai.lotus.app.ui

import ai.lotus.app.BuildConfig
import ai.lotus.app.data.Attachments
import ai.lotus.app.data.AvatarStore
import ai.lotus.app.data.Capabilities
import ai.lotus.app.data.CapabilityStore
import ai.lotus.app.data.ChatEvent
import ai.lotus.app.data.ChatTurn
import ai.lotus.app.data.Conversation
import ai.lotus.app.data.AppPrefs
import ai.lotus.app.data.ConversationStore
import ai.lotus.app.data.DefaultAssistantName
import ai.lotus.app.data.DefaultContextWindow
import ai.lotus.app.data.ImageGen
import ai.lotus.app.data.LlmApi
import ai.lotus.app.data.ProviderConfig
import ai.lotus.app.data.StoredMessage
import ai.lotus.app.data.Tokens
import ai.lotus.app.data.chatStream
import ai.lotus.app.data.ProviderStore
import ai.lotus.app.data.UpdateChecker
import ai.lotus.app.data.Prompts
import ai.lotus.app.data.SelectionStore
import ai.lotus.app.data.isOpeningLine
import ai.lotus.app.data.openingLine
import ai.lotus.app.ui.chat.ChatDisplay
import ai.lotus.app.ui.chat.ChatMessage
import ai.lotus.app.ui.chat.ChatScreen
import ai.lotus.app.ui.home.AttachmentChip
import ai.lotus.app.ui.home.ConversationHistoryPanel
import ai.lotus.app.ui.home.GlassChatPanel
import ai.lotus.app.ui.home.FloatingBackButton
import ai.lotus.app.ui.home.FloatingHistoryButton
import ai.lotus.app.ui.home.FloatingMoreGauge
import ai.lotus.app.ui.home.GlassConfirmDialog
import ai.lotus.app.ui.home.GlassComposerPanel
import ai.lotus.app.ui.home.ModelGroup
import ai.lotus.app.ui.home.GlassPromptDialog
import ai.lotus.app.ui.home.GlassSourcesSheet
import ai.lotus.app.ui.home.GlassToast
import ai.lotus.app.ui.home.GlassToolsPanel
import ai.lotus.app.ui.model.thinkingFor
import ai.lotus.app.ui.model.thinkingHint
import ai.lotus.app.ui.glass.floatGlass
import ai.lotus.app.ui.glass.GlassBackground
import ai.lotus.app.ui.md.MdWorkspace
import ai.lotus.app.ui.glass.LocalGlassBackdrop
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.home.HomeScreen
import ai.lotus.app.ui.home.LotusComposer
import ai.lotus.app.ui.home.LotusComposerHeight
import ai.lotus.app.ui.nav.BarHeight
import ai.lotus.app.ui.nav.BarMargin
import ai.lotus.app.ui.nav.BarWidth
import ai.lotus.app.ui.nav.LotusBottomBar
import ai.lotus.app.ui.nav.LotusTab
import ai.lotus.app.ui.settings.SettingsScreen
import ai.lotus.app.ui.theme.LotusPalette
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** 输入条与底栏之间的间距。 */
private val ComposerBarGap = 22.dp

/**
 * 压缩上下文时喂给模型的那段话。
 *
 * 目的只有一个：把前面聊过的东西**压成以后还能用的背景**，所以要它保住事实、数字、名字、
 * 用户的偏好和结论；不要它评论、不要它续写、不要它输出客套话。
 */
private const val ContextSummaryPrompt =
  """你在压缩一段聊天记录，供之后接着聊时当背景用。要求：
1. 只保留事实、数字、名字、约定、结论、用户明确表达过的偏好和需求；
2. 丢掉寒暄、重复、语气词、和已经被推翻的中间过程；
3. 按要点分组，短句为主，不要评论，不要续写，不要问问题；
4. 用中文输出，总长控制在 400 字以内。"""

// 思考档位与模型列表**不在这里**：它们来自 `ui/model/ModelCatalog.kt`。
// 那一张表就是"思考模式跟着模型走"的唯一来源 —— 换模型会自动把档位夹进新模型的范围。

/**
 * 应用外壳。
 *
 * 结构：`GlassBackground`（背景被录进 backdrop）→ 内容 + 底部固定层（**都是录制盒的兄弟节点**，
 * 所以能采样到背景而不会自反馈）。
 *
 * ## 为什么输入条必须放在这里的固定层，而不是 HomeScreen 的 Column 里
 *
 * 键盘弹出时 `safeDrawing` 会把可用高度压掉 ~300dp。如果输入条在首页那个纵向 Column 里，
 * Column 会被挤爆，**输入条直接被顶出屏幕**，只剩固定在 Box 底部的底栏跟着上移 ——
 * 表现就是"移动的是导航栏，输入框不动，打字什么都看不见"。踩过，别再搬回去。
 *
 * 另外键盘弹出时底栏让位（隐藏）：否则输入条 + 底栏一共 ~110dp 把键盘上方吃光。
 */
@Composable
fun LotusApp(onHomeArtBounds: (Rect) -> Unit = {}) {
  var tab by remember { mutableStateOf(LotusTab.Chat) }
  var draft by remember { mutableStateOf("") }

  // ---- 聊天页（当前是模拟的）----
  //
  // 还没有接模型：进聊天页会铺一段开场白；发出的每条消息都会在 900ms 后收到一句
  // `mockReply` 造的假回复（先放一条空消息当"正在输入"，见 ChatScreen 的三点）。
  // 接真模型时只换这两处：`messages +=` 的位置、以及空消息的填充方式（SSE 流式）。
  var inChat by remember { mutableStateOf(false) }
  val messages = remember { mutableStateListOf<ChatMessage>() }

  // ---- 输入条上的"真按键"：附件 / 思考强度 / 模型切换 ----
  //
  // 这三枚以前是空实现（点了什么都不发生，用户："其他按键现在也是死的 用不了"）。现在：
  //  +  → 系统图片选择器，选完在输入条上方挂一枚玻璃缩略片；
  //  ⚡ → 档位玻璃面板（选中项带小圆点，图标透明度跟着档位走）；
  //  ⇄ → 模型玻璃面板。
  // 助手显示名。**只改名，不改头像**（用户口径：「最多就改个名字，头像别改了」）。
  var composerMenu by remember { mutableStateOf(false) }
  val focus = LocalFocusManager.current
  // 点空白 = 收键盘（用户口径：「打开输入框后点击空白地方应该要跟取消键盘一样的操作啊」）。
  // 挂在 page 这一层：子节点（输入条、按键、气泡）先拿到事件，只有真·空白处的点击会落到底下这里。
  val dismissKeyboardOnBlank = Modifier.pointerInput(focus) {
    detectTapGestures(onTap = { focus.clearFocus() })
  }
  // 退场动画期间还需要知道"关的是哪个面板"（composerMenu 那时已经是 null 了）。
  var expandedMenu by remember { mutableStateOf(false) }
  SideEffect { if (composerMenu) expandedMenu = true }
  var thinkingIndex by remember { mutableStateOf(2) } // 默认「标准」

  // ---- 模型提供方：**用户自己填**，App 不预置任何厂商 / 地址 / key ----
  //
  // 配置落在 filesDir/providers.json，退出再进不用重填（见 data/ProviderStore.kt）。
  val context = LocalContext.current

  // ---- 会话：落盘 / 命名 / 切换 ----
  //
  // 用户口径：「我已经开了很多个新页了，只是都没有固定下来而已，但它却显示没有历史会话，
  // 这是什么鬼？每个会话都没有名称。」所以会话必须**落盘**，而且**每个都要有名字**：
  // 没改过名字 → 拿首条用户消息当名字（见 data/Conversation.kt 的 autoTitle）。
  var conversations by remember { mutableStateOf(ConversationStore.load(context)) }
  // 当前会话 id。null = 还没落地的"新会话"（一条消息都没发）。
  var currentId by remember { mutableStateOf<String?>(null) }
  var historyOpen by remember { mutableStateOf(false) }
  // 右上角那枚玻璃钮开的面板：**上下文 + 会话动作合成一块**（用户：合并一下）。
  var chatPanelOpen by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf(false) }
  // 改名 / 归档这类静默操作也要给用户一个看得见的结果。
  var transientToast by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(transientToast) {
    if (transientToast != null) {
      delay(1600)
      transientToast = null
    }
  }

  var providers by remember { mutableStateOf(ProviderStore.load(context)) }
  var capabilities by remember { mutableStateOf(CapabilityStore.load(context)) }
  // 用户自己传的聊天头像（见 AvatarStore）。改了要立刻重画聊天页。
  var userAvatarPath by remember { mutableStateOf(AvatarStore.path(context)) }

  // ---- 提示词 / 记忆（对话设置里那几个真旋钮）----
  var promptEnabled by remember { mutableStateOf(AppPrefs.promptEnabled(context)) }
  var memoryEnabled by remember { mutableStateOf(AppPrefs.memoryEnabled(context)) }

  // 助手名字**必须落盘**。用户口径：「我尝试给 AI 换名字，换了之后它每次介绍还是原来的那个，
  // 换了名字再重启后也就没了」—— 两个都修：名字落盘 + 开场白跟着改名同步（见 renameAssistant）。
  var assistantName by remember { mutableStateOf(AppPrefs.assistantName(context)) }
  val displayName = assistantName.ifBlank { DefaultAssistantName }

  // 选中的提供方 / 模型也落盘 —— 不然退出再进来就"没选模型"，用户以为白选了。
  val savedSelection = remember { SelectionStore.load(context) }
  var selectedProviderId by remember { mutableStateOf(savedSelection.first) }
  var selectedModelId by remember { mutableStateOf(savedSelection.second) }

  fun saveProviders(next: List<ProviderConfig>) {
    providers = next
    ProviderStore.save(context, next)
    // 当前选中的提供方被删掉了 → 选择一起作废，别留一个指向空气的 id。
    if (selectedProviderId != null && next.none { it.id == selectedProviderId }) {
      selectedProviderId = null
      selectedModelId = ""
      SelectionStore.save(context, null, "")
    }
    // 选中的模型被从这个提供方里删掉了 → 一起作废。
    if (selectedModelId.isNotBlank()) {
      val owner = next.firstOrNull { it.id == selectedProviderId }
      if (owner != null && owner.models.isNotEmpty() && selectedModelId !in owner.models) {
        selectedProviderId = null
        selectedModelId = ""
        SelectionStore.save(context, null, "")
      }
    }
  }

  fun pickModel(providerId: String?, modelId: String) {
    selectedProviderId = providerId
    selectedModelId = modelId
    SelectionStore.save(context, providerId, modelId)
    // 换模型就夹一次档位：不同模型支持的档数不一样，不夹会指到不存在的档。
    thinkingIndex = thinkingIndex.coerceIn(0, thinkingFor(modelId).lastIndex)
  }

  // 当前模型的思考档位。**唯一来源**是 model 的 id（见 thinkingFor）。
  val currentThinking = thinkingFor(selectedModelId)
  val safeThinkingIndex = thinkingIndex.coerceIn(0, currentThinking.lastIndex)
  // 面板最下面那行：这一档实际往请求里写了什么（认不出的模型会额外说明是本地兜底）。
  val thinkingHint =
    thinkingHint(selectedModelId, currentThinking.getOrNull(safeThinkingIndex))

  // 输入条 ⇄ 面板能选的模型 = **只来自用户自己拉的提供方**。
  //
  // ⚠️ 原来这里有个 `.ifEmpty { ModelCatalog… }` 兜底：一个提供方都没配的时候会列出一张
  //    内置模型表（gpt-4o 那一批）。用户明确否过：「我们都没开始设计，就不要默认模型了呀…
  //    而且都是多久以前的模型了？」。所以内置表彻底不用了 —— 没配就是空列表。
  val modelChoices: List<Triple<String?, String, String>> =
    providers
      .filter { it.models.isNotEmpty() }
      .flatMap { p -> p.models.map { Triple(p.id, it, it) } }

  // 附件存**私有目录里的文件路径**（相册给的 content:// 是一次性授权，重启就失效）。
  // 见 data/Attachments.kt。
  var attachmentPath by remember { mutableStateOf<String?>(null) }
  // 视频 / 文件（见 data/Attachments.kt 的 DocRef）。图片之外还得能带这两样。
  var attachDoc by remember { mutableStateOf<ai.lotus.app.data.DocRef?>(null) }
  // 「+」的那张小菜单：图片 / 视频 / 文件。
  var attachMenu by remember { mutableStateOf(false) }
  val pickVideo =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        attachDoc = ai.lotus.app.data.describeDoc(context, uri)
      }
    }
  val pickDoc =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        attachDoc = ai.lotus.app.data.describeDoc(context, uri)
      }
    }
  val pickImage =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        val path = Attachments.import(context, uri)
        if (path != null) attachmentPath = path
      }
    }
  var nextId by remember { mutableStateOf(1L) }
  val scope = rememberCoroutineScope()

  // ---- 「检查更新」的弹层状态 ----
  //
  // ⚠️ 这个弹层**必须住在 content 槽（悬浮玻璃层）里**。原因见 `Glass.kt` 的 GlassBackground：
  //    页面槽里的玻璃只采**纯色底**（避免自采样），弹在页面里的话背后没有内容可糊 —— 看起来
  //    就是一块白板；而且它画在页面内容**之前**，会被卡片盖住，只剩两个按钮露在外面，点哪个
  //    都没反应（而且 checking 卡住时连关都关不掉）。用户报的就是这个。
  /** 模型刚改了哪份提示词文件（几秒后自己消失）。见 [Prompts.applyWrites]。 */
  /** 输入条实测高度（多行会变高），用它摆输入条上面的浮层。 */
  var composerHeight by remember { mutableStateOf(LotusComposerHeight) }
  var writeHint by remember { mutableStateOf("") }
  var changelogOpen by remember { mutableStateOf(false) }
  // 「工具」那一页（它都有哪些工具）。入口在聊天页右上角那块面板里。
  var toolsOpen by remember { mutableStateOf(false) }
  // 应用内阅读器打开的是哪个页面（null = 关着）。
  var readerUrl by remember { mutableStateOf<String?>(null) }
  // 「来源」半屏列表：点消息底部的站点图标先开这个，再点具体一条才进阅读器。
  var sourcesSheet by remember { mutableStateOf<List<String>?>(null) }
  var updateChecking by remember { mutableStateOf(false) }
  var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
  var updateJob by remember { mutableStateOf<Job?>(null) }

  fun runUpdateCheck() {
    if (updateChecking) return
    updateChecking = true
    updateResult = null
    updateJob =
      scope.launch {
        val r = withContext(Dispatchers.IO) { UpdateChecker.check(BuildConfig.VERSION_NAME) }
        updateResult = r
        updateChecking = false
      }
  }

  fun dismissUpdate() {
    updateJob?.cancel()
    updateJob = null
    updateChecking = false
    updateResult = null
  }

  /** 内存里的消息 → 落盘快照。 */
  /** 按 id 改一个会话并落盘。异步任务（比如生成话题）回来时用这个，别用 updateCurrent —— 中途切走会话会写错对象。 */
  fun updateConversation(id: String, block: (Conversation) -> Conversation) {
    conversations = conversations.map { c -> if (c.id == id) block(c) else c }
    ConversationStore.save(context, conversations)
  }

  /** 改当前会话的某一个字段（摘要 / 真实用量），并立刻落盘。 */
  fun updateCurrent(block: (Conversation) -> Conversation) {
    val id = currentId ?: return
    updateConversation(id, block)
  }

  fun stored(): List<StoredMessage> =
    messages.map {
      StoredMessage(
        it.id,
        it.fromUser,
        it.text,
        it.at,
        it.model,
        it.tokens,
        it.imagePath,
        it.reasoning,
        it.tools,
        it.toolLog,
        it.sources,
      )
    }

  /** 把当前会话写回列表并落盘。没消息的会话不落盘 —— 历史里不留空壳。 */
  fun saveCurrent() {
    val id = currentId ?: return
    val snapshot = stored()
    if (snapshot.isEmpty()) {
      // 删空 / 编辑掉最后一条后不能把旧消息继续留在 conversations.json 里；
      // 否则界面已经空了，退出再进却又“复活”。这个会话本身也一并移除。
      conversations = conversations.filterNot { it.id == id }
      ConversationStore.save(context, conversations)
      currentId = null
      return
    }
    // 关键：保存 ≠ 活跃。只在**真的产生了更晚的消息**时更新历史排序；
    // 单纯点开一个旧会话、退出、改名、记 token，都不能把它顶到最前面。
    val newestAt = snapshot.maxOfOrNull { it.at } ?: 0L
    conversations =
      conversations.map { c ->
        if (c.id != id) c
        else c.copy(messages = snapshot, updatedAt = maxOf(c.updatedAt, newestAt))
      }
    ConversationStore.save(context, conversations)
  }

  /** 第一次发消息时才真的建会话。 */
  fun ensureConversation() {
    if (currentId == null) {
      val now = System.currentTimeMillis()
      val id = UUID.randomUUID().toString()
      conversations =
        listOf(Conversation(id = id, createdAt = now, updatedAt = now)) + conversations
      currentId = id
    }
    saveCurrent()
  }

  fun closeOverlays() {
    composerMenu = false
    historyOpen = false
    chatPanelOpen = false
    // 「+」那张小菜单也要算浮层 —— 忘了它，点空白就关不掉（真出现过）。
    attachMenu = false
  }

  /** 导入加密备份后，把磁盘上的整套状态重新装回内存。 */
  fun reloadImportedData() {
    conversations = ConversationStore.load(context)
    providers = ProviderStore.load(context)
    capabilities = CapabilityStore.load(context)
    userAvatarPath = AvatarStore.path(context)
    promptEnabled = AppPrefs.promptEnabled(context)
    memoryEnabled = AppPrefs.memoryEnabled(context)
    assistantName = AppPrefs.assistantName(context)
    val selection = SelectionStore.load(context)
    selectedProviderId = selection.first
    selectedModelId = selection.second
    thinkingIndex = thinkingIndex.coerceIn(0, thinkingFor(selectedModelId).lastIndex)
    messages.clear()
    currentId = null
    inChat = false
    draft = ""
    attachmentPath = null
    attachDoc = null
    closeOverlays()
    focus.clearFocus()
  }

  fun newChat() {
    saveCurrent()
    messages.clear()
    currentId = null
    inChat = false
    draft = ""
    attachmentPath = null
    closeOverlays()
    focus.clearFocus()
  }

  fun openConversation(id: String) {
    saveCurrent()
    val target = conversations.firstOrNull { it.id == id }
    if (target != null) {
      messages.clear()
      messages.addAll(
        target.messages.map {
          ChatMessage(
            it.id,
            it.fromUser,
            it.text,
            it.at,
            it.model,
            it.tokens,
            it.imagePath,
            it.reasoning,
            it.tools,
            it.toolLog,
            it.sources,
          )
        }
      )
      currentId = id
      nextId = (target.messages.maxOfOrNull { it.id } ?: 0L) + 1
      inChat = true
      // ⚠️ 必须切回聊天 tab：历史面板从任何一个 tab 都能打开（设置页现在也有入口），
      //    少了这一行就会出现"点了会话但停在设置页，看着像没反应"。
      tab = LotusTab.Chat
    }
    attachmentPath = null
    closeOverlays()
    focus.clearFocus()
  }

  fun renameConversation(title: String) {
    val id = currentId ?: return
    conversations =
      conversations.map { c -> if (c.id != id) c else c.copy(title = title.trim()) }
    ConversationStore.save(context, conversations)
    renaming = false
    transientToast = "对话名称已保存"
  }

  /**
   * 「删除」= **归档**（软删除）。
   *
   * 用户口径：「我尝试删除了一个会话，在已归档会话那里不会存在，还是没做好」。
   * 以前这里是直接从盘上抹掉，误点一下就找不回来。现在只打一个 `archived` 标记：
   * 不进历史列表，内容一条不删，去「设置 → 已归档」能恢复。
   */
  fun deleteConversation() {
    val id = currentId ?: return
    saveCurrent()
    updateConversation(id) { it.copy(archived = true) }
    messages.clear()
    currentId = null
    inChat = false
    deleting = false
    transientToast = "会话已移到已归档"
  }

  /** 从归档里恢复一个会话。 */
  fun restoreArchived(id: String) {
    updateConversation(id) { it.copy(archived = false) }
  }

  /** 永久删除所有归档会话（已归档页那个"再点一次"的动作）。 */
  fun purgeArchived() {
    conversations = conversations.filterNot { it.archived }
    ConversationStore.save(context, conversations)
  }

  /**
   * 给当前会话生成一句「话题」（= 这段在聊什么）。
   *
   * ⚠️ **一个会话只跑一次**（[Conversation.topicTried] 把关），失败也不重试 ——
   * 每轮都跑一次总结正是 Codex 那种"聊一句花两份钱"的来源。这里多花的只有一次：
   * 首轮答完、上下文还很小的时候，顺带要一个 12 字以内的话题名（max_tokens 卡死）。
   */
  fun summarizeTopic(id: String) {
    val conv = conversations.firstOrNull { it.id == id } ?: return
    if (conv.topic.isNotBlank() || conv.topicTried) return
    val provider = providers.firstOrNull { it.id == selectedProviderId } ?: return
    if (!provider.isComplete || selectedModelId.isBlank()) return
    val transcript =
      conv.messages
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { (if (it.fromUser) "用户" else "助手") + "：" + it.text }
    if (transcript.isBlank()) return
    // 先标记，避免流还没回来又发一条时重复触发。
    updateConversation(id) { it.copy(topicTried = true) }
    scope.launch {
      var acc = ""
      LlmApi.chatStream(
          baseUrl = provider.baseUrl,
          apiKey = provider.apiKey,
          model = selectedModelId,
          turns =
            listOf(
              ChatTurn(
                "system",
                "给下面这段对话起一个话题名：不超过 12 个字，只输出这个名字本身，" +
                  "不要标点、不要引号、不要解释。",
              ),
              ChatTurn("user", transcript),
            ),
          reasoningEffort = null,
          maxTokens = 32,
        )
        .collect { event ->
          if (event is ChatEvent.Delta) acc += event.text
        }
      val topic = acc.trim().trim('“', '”', '"', '。', '，', ',', '、', '\n').take(20)
      if (topic.isNotBlank()) updateConversation(id) { it.copy(topic = topic) }
    }
  }

  // ---- 上下文用量 ----
  //
  // ⚠️ 两个数必须分清，别把估的当成真的给用户看：
  //   · **真实值** = 服务端在流末尾回的 `usage.prompt_tokens`（靠请求里的
  //     `stream_options.include_usage`，见 data/LlmApi.kt）。这才是"这次真正吃掉的上下文"。
  //   · **估算值** = 客户端按字数估的（见 data/Conversation.kt 的 Tokens）。只在拿不到真实值
  //     时兜底，而且面板上要写明"本地估算"。
  // 分母是**用户自己在提供方里填的**窗口大小 —— OpenAI 兼容的 /models 不返回这个字段，
  // 客户端猜不出来，所以宁可让用户填，也不假装知道。
  val selectedProviderConfig = providers.firstOrNull { it.id == selectedProviderId }
  val contextWindow = selectedProviderConfig?.contextWindow ?: DefaultContextWindow
  val currentConversation = conversations.firstOrNull { it.id == currentId }
  val summaryCutoff = currentConversation?.summaryUpTo ?: 0L
  val estimatedRequestMessages =
    if (currentConversation?.summary?.isNotBlank() == true && summaryCutoff > 0L) {
      stored().filter { it.id > summaryCutoff }
    } else {
      stored()
    }
  val estimatedTokens =
    Tokens.estimate(estimatedRequestMessages) +
      Tokens.estimate(currentConversation?.summary.orEmpty())
  val realTokens = currentConversation?.promptTokens ?: 0
  val usedTokens = if (realTokens > 0) realTokens else estimatedTokens
  val usageIsReal = realTokens > 0
  var compressing by remember { mutableStateOf(false) }

  /** 正在生图的那条回复 id。聊天页据此在气泡里显示"正在画图…"。 */
  var paintingId by remember { mutableStateOf<Long?>(null) }
  /** 正在流式回答的那条消息 id。用来控制思考玻璃自动出现 / 收起。 */
  var replyingId by remember { mutableStateOf<Long?>(null) }

  // 「记住这段」——手动触发的记忆落盘（见 rememberConversation）。
  var rememberBusy by remember { mutableStateOf(false) }
  var rememberHint by remember { mutableStateOf("") }

  /**
   * 改助手名字：落盘 + **把已经写出去的开场白一起改掉**。
   *
   * 开场白是一条普通消息，所以光改设置是没用的 —— 旧会话里那句"我是 Lotus"会一直躺在那里。
   * 这里对所有会话（含当前内存里这条）做一次替换；用户自己打的字不受影响。
   */
  fun renameAssistant(next: String) {
    assistantName = next
    AppPrefs.setAssistantName(context, next)
    val line = openingLine(next.trim().ifBlank { DefaultAssistantName })
    for (i in messages.indices) {
      val m = messages[i]
      if (!m.fromUser && isOpeningLine(m.text)) messages[i] = m.copy(text = line)
    }
    conversations =
      conversations.map { c ->
        if (c.id == currentId) c
        else
          c.copy(
            messages =
              c.messages.map { if (!it.fromUser && isOpeningLine(it.text)) it.copy(text = line) else it }
          )
      }
    saveCurrent()
  }

  /**
   * 真正发出去的那串上下文 = **摘要（有的话）+ 尚未被摘要覆盖的新消息**。
   *
   * 原文从不从界面删除；压缩只改变请求边界。这样既保留完整聊天记录，又真的省 token。
   */
  fun requestTurns(excludeId: Long? = null): List<ChatTurn> {
    val all = messages.filter { it.id != excludeId }.filter { it.text.isNotBlank() }
    val conv = conversations.firstOrNull { it.id == currentId }
    val summary = conv?.summary.orEmpty().trim()
    val cutoff = if (summary.isNotBlank()) conv?.summaryUpTo ?: 0L else 0L
    val recent = if (cutoff > 0L) all.filter { it.id > cutoff } else all
    val turns = mutableListOf<ChatTurn>()
    // 提示词 + 记忆拼在**最前面**（见 data/Prompts.kt）。没写内容时这里是空的。
    Prompts
      .systemBlocks(
        context = context,
        assistantName = displayName,
        withPrompt = promptEnabled,
        withMemory = memoryEnabled,
        // 自动记忆 = 允许模型在同一次回答里把"该记的事"用隐藏标记带回来（客户端落盘）。
        withAutoMemory = AppPrefs.autoMemoryEnabled(context),
        // 生图协议只在用户真的配好了生图服务时才注入（不配 = 不占每轮 token）。
        imageReady = capabilities.ready(ai.lotus.app.data.CapabilityKind.Image),
        // 记忆分层：默认只带热指针；「全量记忆」开关打开才带全文。
        fullMemory = AppPrefs.fullMemoryEnabled(context),
        // 模型之前点名要的记忆（见 Prompts.RecallProtocol）—— 纯本地按关键词取段落，零额外调用。
        recallQueries = conv?.recallKeys.orEmpty(),
        // 兜底：拿用户这句原话自己去档案柜里匹配（实测模型经常不写 <!--recall-->，
        // 所以"记不记得住"不能全押在它自觉上）。
        recallProbe = recent.lastOrNull { it.fromUser }?.text.orEmpty(),
      )
      .forEach { turns += ChatTurn("system", it) }
    summary.takeIf { it.isNotBlank() }?.let {
      turns += ChatTurn("system", "以下是更早的对话摘要，当作已经聊过的背景：\n$it")
    }
    // 图只挂**最近一条用户消息**上：历史里的图再发一次纯属烧 token（用户最在意这个），
    // 而且看图模型本来也只需要看最新那张。
    val lastUserIndex = recent.indexOfLast { it.fromUser }
    recent.forEachIndexed { idx, m ->
      val shot =
        if (idx == lastUserIndex && m.imagePath.isNotBlank()) Attachments.dataUrl(m.imagePath)
        else null
      turns += ChatTurn(if (m.fromUser) "user" else "assistant", m.text, shot)
    }
    return turns
  }

  /**
   * 压缩：把**尚未被摘要覆盖的消息**继续并入摘要，之后请求只发“摘要 + 新消息”。
   *
   * 原文一条都不删 —— 界面里照样能往上翻。压缩只是让它不再进请求。
   */
  fun compressContext() {
    if (compressing) return
    val provider = providers.firstOrNull { it.id == selectedProviderId }
    if (provider == null || !provider.isComplete || selectedModelId.isBlank()) return
    val conv = conversations.firstOrNull { it.id == currentId } ?: return
    val cutoff = if (conv.summary.isNotBlank()) conv.summaryUpTo else 0L
    val pending = messages.filter { it.text.isNotBlank() && (cutoff <= 0L || it.id > cutoff) }
    if (pending.isEmpty()) return
    val transcript =
      pending
        .joinToString("\n") { (if (it.fromUser) "用户" else "助手") + "：" + it.text }
    val previousSummary = conv.summary.trim()
    val input =
      buildString {
        if (previousSummary.isNotEmpty()) {
          append("已有摘要：\n").append(previousSummary).append("\n\n新增对话：\n")
        }
        append(transcript)
      }
    val newCutoff = pending.maxOf { it.id }
    compressing = true
    scope.launch {
      var acc = ""
      try {
        LlmApi.chatStream(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            model = selectedModelId,
            turns =
              listOf(
                ChatTurn("system", ContextSummaryPrompt),
                ChatTurn("user", input),
              ),
            reasoningEffort = null,
            maxTokens = 800,
          )
          .collect { event ->
            when (event) {
              is ChatEvent.Delta -> acc += event.text
              is ChatEvent.Failed -> acc = ""
              else -> Unit
            }
          }
        val text = acc.trim()
        if (text.isNotEmpty()) {
          updateConversation(conv.id) {
            it.copy(summary = text, summaryUpTo = newCutoff, promptTokens = 0)
          }
        }
      } finally {
        compressing = false
      }
    }
  }

  /**
   * 「记住这段」：把**当前这段对话**交给模型压成几条长期记忆，按主题合并进 USER / MEMORY。
   *
   * 为什么要有手动这一下：压缩只在超过阈值时才发生，没压到的对话就永远留不下东西。
   * 用户口径：「有些人如果没到压缩点的话，就永远不会有这段记忆，也就是说他得手动点」。
   *
   * 这一下**只在用户点的时候跑一次模型**，不是每轮都跑 —— 不会变成 Codex 那种额外开销。
   * 写入使用与自动记忆相同的按主题 upsert；用户手写区不动，也不会再长出“记忆快照”尾巴。
   */
  fun rememberConversation() {
    if (rememberBusy) return
    val provider = providers.firstOrNull { it.id == selectedProviderId }
    if (provider == null || !provider.isComplete || selectedModelId.isBlank()) {
      rememberHint = "还没配好模型，去设置里选一个"
      return
    }
    val transcript =
      messages
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { (if (it.fromUser) "用户" else "助手") + "：" + it.text }
    if (transcript.isBlank()) return
    rememberBusy = true
    rememberHint = ""
    scope.launch {
      var acc = ""
      LlmApi.chatStream(
          baseUrl = provider.baseUrl,
          apiKey = provider.apiKey,
          model = selectedModelId,
          turns =
            listOf(
              ChatTurn(
                "system",
                "从下面这段对话里提取**值得长期记住**的信息，写成 Markdown 短条目（每条一行，以 - 开头）。" +
                  "每条固定使用 `- 主题：内容` 的格式，主题要短、稳定、可复用（例如 称呼 / 爱好 / 项目 / 决定 / 偏好）。" +
                  "只收：身份、长期偏好、在做的事、已定下的结论、约定、忌讳。" +
                  "丢掉：寒暄、临时情绪、一次性的问题、已经推翻的中间过程。不要评论，不要解释，" +
                  "最多 8 条；没有值得记的就只输出 `NONE`。",
              ),
              ChatTurn("user", transcript),
            ),
          reasoningEffort = null,
          maxTokens = 400,
        )
        .collect { event ->
          when (event) {
            is ChatEvent.Delta -> acc += event.text
            is ChatEvent.Failed -> acc = ""
            else -> Unit
          }
        }
      val body = acc.trim()
      if (body.isEmpty() || body == "NONE") {
        rememberHint = "这次没记住，要不再点一次"
      } else {
        val lines =
          body
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("```") }
            .map { if (it.startsWith("-") || it.startsWith("#")) it else "- $it" }
        val changed = Prompts.appendAutoMemory(context, lines)
        rememberHint =
          if (changed.isEmpty()) "没有新的内容需要写入"
          else "已整理进 ${changed.joinToString(" / ")}（${lines.size} 条）"
      }
      rememberBusy = false
    }
  }

  /**
   * 发一条，然后**真的去打模型**（流式）。
   *
   * 一次提问 = 一次调用，没有隐藏的额外请求：历史直接就是 `messages`，没有压缩、没有复盘。
   */
  /**
   * 真的去打一次模型（[send] 和「重新回答」共用）。
   *
   * 一次提问 = 一次调用，没有隐藏的额外请求：历史直接就是 `messages`，没有压缩、没有复盘。
   * 唯一的例外是生图 —— 模型写了 `<!--image: …-->` 时客户端再发**一次生图 HTTP 调用**
   * （不是 LLM 调用，不循环、不重喂上下文）。
   */
  fun requestReply() {
    val provider = providers.firstOrNull { it.id == selectedProviderId }
    if (provider == null || !provider.isComplete || selectedModelId.isBlank()) {
      messages +=
        ChatMessage(
          nextId++,
          false,
          "还没配好模型。去「设置 → 模型提供方」填 Base URL 和 API Key，点一次「测试连接」，" +
            "再到「默认模型」里选一个。",
        )
      return
    }

    val replyId = nextId++
    // 记下"这条是谁答的"：模型可以中途换，用户要能一眼看出（对话设置里的「显示模型」）。
    // at 落成当前时间，答完就是这条的完成时间。
    messages +=
      ChatMessage(
        replyId,
        false,
        "",
        System.currentTimeMillis(),
        model = selectedModelId,
      )
    replyingId = replyId

    // 真正发出去的上下文（摘要 + 最近 N 条），别在流式过程中反复读整张列表。
    val turns = requestTurns(excludeId = replyId)
    // 联网搜索：设置里开的那个开关。开着时请求体只多一条 `web_search` 声明，
    // 搜索本身在上游做，客户端**不会**多发请求（见 LlmApi.chatStream 的注释）。
    //
    // ⚠️ 只有实测支持原生联网的模型才发这条声明。DeepSeek 等上游会直接拒绝
    // `tools:[{"type":"web_search"}]` 并返回 422；把不认识的字段硬塞过去不是“联网失败”，
    // 而是整轮聊天一起失败。未检测也按不发送处理，优先保证聊天可用。
    val searchProfile =
      selectedProviderId?.let { pid ->
        ai.lotus.app.data.ModelCapabilityStore.load(context, pid, selectedModelId)
      }
    val webSearch =
      AppPrefs.webSearchEnabled(context) &&
        searchProfile?.nativeWebSearch == ai.lotus.app.data.CapabilityStatus.Supported
    // 只有"这一档真的对应一个字段"时才传；不传 = 上游走它自己的默认。
    val effort = currentThinking[safeThinkingIndex].reasoningEffort

    /**
     * 跑一次流式请求。返回 true = **一个字都没吐出来就失败了**（这种才值得降档重发）。
     *
     * 为什么不重试"吐了一半才断"的：那属于网络问题，重发会把半截回答接成两段，反而更脏。
     */
    // 自动记忆（同一次调用内完成，不额外发请求）：
    //   模型写隐藏标记 → 客户端剥掉后按语义分区：用户本人去 USER.md，其他长期事实去
    //   MEMORY.md，项目/决定/教训等去 memory/ 档案柜。仍是一次模型调用，不额外发请求。
    val autoMemory = AppPrefs.autoMemoryEnabled(context)
    val imageReady = capabilities.ready(ai.lotus.app.data.CapabilityKind.Image)
    val memoEntries = mutableListOf<String>()
    val userEntries = mutableListOf<String>()
    var pendingArchives: List<ai.lotus.app.data.Prompts.ArchiveWrite> = emptyList()
    var imagePrompt: String? = null
    // 模型要求追加进自己提示词文件的内容（`<!--write: SOUL.md | …-->`）。
    var pendingWrites: List<ai.lotus.app.data.Prompts.FileWrite> = emptyList()
    // 模型要求**整理**的文件（`<!--tidy: MEMORY.md | …-->`）：只重写 AI 自己那一段。
    var pendingTidy: List<ai.lotus.app.data.Prompts.FileWrite> = emptyList()
    // 模型点名要取的记忆关键词（`<!--recall: …-->`）：下一轮按它们去档案柜里取段落。
    var pendingRecalls: List<String> = emptyList()
    var rawReply = ""
    // 上游给的推理过程（没有就一直是空串 —— 那界面上不会出现"思考过程"那块）。
    // ⚠️ 跟 rawReply 分开攒：推理里的文字**不参与**标记解析，别让思考过程里的半截
    //    `<!--` 被当成指令。
    var reasoningBuf = ""
    // 上游报回来的"它读过哪些页面"（annotations / citations）。一把都没报就一直是空的 ——
    // 聊天页那行「访问记录」就不会出现。**不拿正文里的链接冒充**。
    val sourceBuf = linkedSetOf<String>()

    suspend fun runStream(attemptEffort: String?): Boolean {
      var gotText = false
      var failed = false
      // 重发时从头再来，别把上一次的残留标记接进这一轮。
      rawReply = ""
      reasoningBuf = ""
      sourceBuf.clear()
      memoEntries.clear()
      userEntries.clear()
      pendingArchives = emptyList()
      imagePrompt = null
      pendingWrites = emptyList()
      pendingTidy = emptyList()
      pendingRecalls = emptyList()
      LlmApi.chatStream(
          baseUrl = provider.baseUrl,
          apiKey = provider.apiKey,
          model = selectedModelId,
          turns = turns,
          reasoningEffort = attemptEffort,
          webSearch = webSearch,
        )
        .collect { event ->
          val index = messages.indexOfFirst { it.id == replyId }
          if (index < 0) return@collect
          when (event) {
            is ChatEvent.ModelInfo -> Unit
            is ChatEvent.Delta -> {
              gotText = true
              rawReply += event.text
              // 边收边剥：标记可能被流切碎，splitMemos 会把"还没闭合的 <!--"整段藏起来，
              // 所以用户不会看到半截 `<!--memo` 闪过去。
              val d = Prompts.splitDirectives(rawReply)
              memoEntries.clear()
              memoEntries += d.memos
              userEntries.clear()
              userEntries += d.users
              pendingArchives = d.archives
              imagePrompt = d.image
              pendingWrites = d.writes
              pendingTidy = d.tidy
              pendingRecalls = d.recalls
              messages[index] = messages[index].copy(text = d.visible)
            }
            is ChatEvent.Sources -> {
              // 上游报回了它读过的页面 —— 攒起来给"访问记录"那行用。
              sourceBuf.addAll(event.urls)
            }
            is ChatEvent.Reasoning -> {
              // 思考过程边收边显示：用户能在等答案的时候看见它真的在干活，而不是干等三个点。
              reasoningBuf += event.text
              messages[index] = messages[index].copy(reasoning = reasoningBuf)
            }
            is ChatEvent.Usage -> {
              // 服务端报的真实 prompt_tokens —— 存下来，用量面板从此显示实测值。
              updateCurrent { it.copy(promptTokens = event.promptTokens) }
              // 这一条自己的 token（prompt + completion），给「显示 token」那个开关用。
              val i = messages.indexOfFirst { it.id == replyId }
              if (i >= 0) {
                messages[i] =
                  messages[i].copy(tokens = event.promptTokens + event.completionTokens)
              }
            }
            is ChatEvent.Failed -> {
              failed = true
              val old = messages[index].text
              messages[index] =
                messages[index].copy(text = "⚠️ ${event.message}".let { if (old.isBlank()) it else old })
            }
            ChatEvent.Done -> {
              val i = messages.indexOfFirst { it.id == replyId }
              if (i >= 0) messages[i] = messages[i].copy(at = System.currentTimeMillis())
            }
          }
          // 每一片回来都落一次盘太费 IO，但流一结束必须落 —— 否则退出 App 就丢这次回答。
          if (event is ChatEvent.Done || event is ChatEvent.Failed) saveCurrent()
        }
      return failed && !gotText
    }

    scope.launch {
      val rejected = runStream(effort)
      // 上游不认这一档（比如 gpt-6-luna 的 ultra）→ 把思考参数摘掉重发一次。
      // 面板里给的是**官方档位名**，认不认由上游说话；这里保证"选了最猛的一档不会直接聊崩"。
      if (rejected && effort != null) {
        val index = messages.indexOfFirst { it.id == replyId }
        if (index >= 0) messages[index] = messages[index].copy(text = "")
        runStream(null)
      }
      // 模型自己记下的东西 → 按语义分开落盘。用户手写的内容永远不动。
      val memoryFiles = linkedSetOf<String>()
      if (autoMemory) {
        if (memoEntries.isNotEmpty()) memoryFiles += Prompts.appendAutoMemory(context, memoEntries)
        if (userEntries.isNotEmpty()) memoryFiles += Prompts.appendUserMemory(context, userEntries)
        if (memoEntries.isEmpty() && userEntries.isEmpty()) {
          // 兜底：用户明确让“记住”，但模型漏写了标记 —— 本地照样存，免得它嘴上说记住了其实没存。
          memoryFiles +=
            Prompts.memoryIntentFallback(
              context,
              messages.lastOrNull { it.fromUser }?.text.orEmpty(),
            )
        }
      }
      // 模型点名要的记忆 → 把关键词记在会话上，下一轮按它去档案柜里取段落。
      // 这一步**不发请求**：只是把几个词存下来，取内容时也是本地字符串匹配。
      if (pendingRecalls.isNotEmpty()) {
        val keys = pendingRecalls.map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isNotEmpty()) {
          updateCurrent { it.copy(recallKeys = (it.recallKeys + keys).distinct().takeLast(8)) }
          saveCurrent()
          writeHint = "已读取记忆：" + keys.joinToString("、")
        }
      }
      // 模型要求改自己的提示词 → **只追加**进那 5 份文件（白名单外的丢掉）。
      // 这一步是"打通底层"的关键：模型说改了，客户端必须真的落盘，不能只嘴上说。
      val wrote =
        if (pendingWrites.isNotEmpty()) {
          ai.lotus.app.data.Prompts.applyWrites(context, pendingWrites)
        } else {
          // 兜底：用户点名"在 SOUL.md 里加一条：…"而模型漏了标记 —— 本地自己写，别让它成了假动作。
          ai.lotus.app.data.Prompts.writeIntentFallback(
            context,
            messages.lastOrNull { it.fromUser }?.text.orEmpty(),
          )
        }
      val archived = Prompts.applyArchives(context, pendingArchives)
      if (wrote.isNotEmpty()) writeHint = "已写入 " + wrote.joinToString(" / ")
      if (memoryFiles.isNotEmpty()) writeHint = "已记住：" + memoryFiles.joinToString(" / ")
      // 整理：只覆盖 AI 自己写的那一段，用户手写的部分原样在前。
      var tidied = false
      pendingTidy.take(1).forEach { w ->
        if (ai.lotus.app.data.Prompts.tidyFile(context, w.file, w.text)) {
          writeHint = "已整理 " + w.file.trim().uppercase() + ".md"
          tidied = true
        }
      }
      // 生图：① 模型写了 `<!--image: …-->` 就用它；② 模型忘了写、但用户明说"画一张" → 直接用那句话。
      // 第 ② 条是兜底：不能把"能不能出图"全押在模型愿不愿意写标记上。
      val lastUserText = messages.lastOrNull { it.fromUser }?.text.orEmpty()
      val shot = imagePrompt ?: if (imageReady) Prompts.drawIntentPrompt(lastUserText) else null
      var didImage = false
      if (imageReady && shot != null) {
        // 画图期间挂上"正在画图…"的指示 —— 生图要几十秒，没有指示用户会以为没反应。
        paintingId = replyId
        val res = ImageGen.generate(context, capabilities.image, shot)
        paintingId = null
        val i = messages.indexOfFirst { it.id == replyId }
        if (i >= 0) {
          if (res.path != null) {
            messages[i] = messages[i].copy(imagePath = res.path)
            didImage = true
          } else {
            messages[i] = messages[i].copy(text = (messages[i].text + "\n\n⚠️ " + res.error).trim())
          }
        }
        saveCurrent()
      }
      // ---- 这一轮 AI 到底动用了什么（**客户端自己记账**，不是听模型自吹）----
      //
      // 用户口径：「他会调用哪些工具（比如记忆等），这些得加上去，要不然太简陋了。」
      // 所以这里把"真的发生了的动作"记在这条回答上，聊天页用一排小玻璃片显示出来。
      // 只有**没发生就不写** —— 一排全是点亮的小标，比不写还误导人。
      val usedTools =
        buildList {
          if (memoryFiles.contains("USER.md")) add("用户记忆")
          if (memoryFiles.contains("MEMORY.md")) add("长期记忆")
          if (wrote.any { it in setOf("IDENTITY.md", "SOUL.md", "AGENTS.md") }) add("设定")
          if (archived.isNotEmpty()) add("归档")
          if (tidied) add("整理")
          if (webSearch) add("联网")
          if (didImage) add("生图")
        }
      // 每个动作的**明细** —— 用户口径：「那些调用工具我也不能看详细内容」。
      // 光一个"记住"标签看不出写了什么，所以把真正落盘的内容/文件名/画面描述都记下来。
      val toolLog =
        buildList {
          if (memoryFiles.isNotEmpty()) {
            val detail =
              (userEntries + memoEntries).take(2).joinToString("；").take(90)
                .ifBlank { memoryFiles.joinToString(" / ") }
            add("记忆｜" + memoryFiles.joinToString(" / ") + " · " + detail)
          }
          val settingFiles = wrote.filter { it in setOf("IDENTITY.md", "SOUL.md", "AGENTS.md") }
          if (settingFiles.isNotEmpty()) add("设定｜" + settingFiles.joinToString(" / "))
          if (archived.isNotEmpty()) add("归档｜" + archived.joinToString(" / "))
          if (tidied) {
            val f = pendingTidy.firstOrNull()?.file?.trim()?.uppercase()
            add("整理｜" + (f?.let { it + ".md" } ?: "记忆段"))
          }
          if (webSearch) {
            // 上游回了页面明细 → 写清读了几页；没回 → **如实说没回**，不写"由服务端检索"这种
            // 听着像真检索过的话（用户口径：「这功能必须要有」，但不许编）。
            add(
              if (sourceBuf.isNotEmpty()) "联网｜上游回了 ${sourceBuf.size} 个页面"
              else "联网｜上游未返回页面明细"
            )
          }
          if (didImage && !shot.isNullOrBlank()) add("生图｜" + shot.take(70))
        }
      val ti = messages.indexOfFirst { it.id == replyId }
      if (ti >= 0) {
        // 即使这一轮没有工具，也要把上游返回的来源落盘；否则界面明明拿到了 URL，
        // 退出会话再回来就丢了。工具列表为空是事实，不补假数据。
        messages[ti] =
          messages[ti].copy(tools = usedTools, toolLog = toolLog, sources = sourceBuf.toList())
      }
      // 当天的对话流水：写进 md/memory/YYYY-MM-DD.md。**纯本地、零 token**（见 Prompts.appendDailyLog）。
      // 这就是 OpenClaw 那种"memory 里按天记日志"的路子 —— 只不过我们不把它塞回每轮请求里。
      val li = messages.indexOfFirst { it.id == replyId }
      if (li >= 0) {
        Prompts.appendDailyLog(
          context = context,
          userText = messages.lastOrNull { it.fromUser }?.text.orEmpty(),
          assistantText = messages[li].text,
        )
      }
      // 首轮答完 → 给这个会话起一个话题（一个会话只跑一次，见 summarizeTopic）。
      currentId?.let { summarizeTopic(it) }
      // 落地后过程面板自动收起；保存时 updatedAt 已按最后一条消息时间更新。
      if (replyingId == replyId) replyingId = null
      saveCurrent()
    }
  }

  /**
   * 引用一条消息：把原文按 Markdown 引用块塞进输入框（`> 原文`），用户接着写。
   * 不额外请求、不改历史 —— 只是把上下文**显式**摆到用户眼前。
   */
  fun quoteMessage(text: String) {
    val quoted = text.trim().lines().joinToString("\n") { "> $it" }
    if (quoted.isBlank()) return
    draft = if (draft.isBlank()) "$quoted\n\n" else "$quoted\n\n$draft"
    closeOverlays()
  }

  /** 摘要覆盖到了将被截断的历史时，摘要已经失去前提，必须整段作废。 */
  fun invalidateSummaryFrom(cutId: Long) {
    val conv = conversations.firstOrNull { it.id == currentId } ?: return
    if (conv.summary.isNotBlank() && conv.summaryUpTo >= cutId) {
      updateConversation(conv.id) { it.copy(summary = "", summaryUpTo = 0L, promptTokens = 0) }
    }
  }

  /**
   * 编辑一条用户消息并重发：**从这条起**的历史全部丢掉，原文放回输入框让用户改。
   *
   * 为什么丢掉后面的：改了前提，后面那些回答就是基于旧前提产生的，留着只会自相矛盾。
   */
  fun editMessage(id: Long) {
    val idx = messages.indexOfFirst { it.id == id }
    if (idx < 0 || !messages[idx].fromUser) return
    invalidateSummaryFrom(id)
    draft = messages[idx].text
    while (messages.size > idx) messages.removeAt(messages.lastIndex)
    saveCurrent()
    closeOverlays()
  }

  /**
   * 撤回一条消息：从这条（含）往后全部删掉，落盘。不留"半截"上下文。
   */
  fun deleteMessage(id: Long) {
    val idx = messages.indexOfFirst { it.id == id }
    if (idx < 0) return
    invalidateSummaryFrom(id)
    while (messages.size > idx) messages.removeAt(messages.lastIndex)
    saveCurrent()
    closeOverlays()
  }

  /**
   * 重新回答：删掉这条助手消息，用**它之前**的历史再打一次模型。
   *
   * 跟 send() 共用 [requestReply] —— 一次提问仍然只是一次调用，只是这次不重复添加用户那条消息。
   */
  fun regenerate(id: Long) {
    val idx = messages.indexOfFirst { it.id == id }
    if (idx < 0 || messages[idx].fromUser) return
    invalidateSummaryFrom(id)
    while (messages.size > idx) messages.removeAt(messages.lastIndex)
    saveCurrent()
    requestReply()
  }

  fun send() {
    val text = draft.trim()
    // 空手点发送 = "开始聊天"：进聊天页 + 铺开场白。
    if (!inChat) {
      inChat = true
      if (messages.isEmpty()) {
        messages +=
          ChatMessage(
            nextId++,
            false,
            openingLine(displayName),
            System.currentTimeMillis(),
          )
      }
    }
    // ⚠️ 只带图、没打字也要能发（用户口径：「我单独直接发一张图片，发不出去，狂按都没用」）。
    val shot = attachmentPath
    val doc = attachDoc
    if (text.isEmpty() && shot == null && doc == null) return
    draft = ""
    attachmentPath = null
    attachDoc = null
    // 视频 / 文件**原样进这条消息**：
    //  · 纯文本文件 → 内容真的贴进去（模型能用上）；
    //  · 视频 / 二进制 → 只带名字和大小，并明说看不到内容（不许假装看过）。
    val body =
      buildString {
        append(text)
        if (doc != null) {
          if (isNotEmpty()) append("\n\n")
          append("[附件：").append(doc.name).append("（")
            .append(ai.lotus.app.data.humanBytes(doc.bytes)).append("）]")
          val content = doc.text
          if (content != null) {
            append("\n```\n").append(content.take(20000)).append("\n```")
            if (content.length > 20000) append("\n（文件太长，只带了前 20000 个字符）")
          } else if (doc.name.lowercase().let { n ->
              listOf(".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v").any { n.endsWith(it) }
            }
          ) {
            append("\n（这是一段视频，我只会拿到文件名，看不到里面的画面）")
          }
        }
      }
    messages +=
      ChatMessage(
        nextId++,
        true,
        body,
        System.currentTimeMillis(),
        imagePath = shot.orEmpty(),
      )
    // 会话在这里才真正诞生 + 落盘（首次发消息时）。
    ensureConversation()

    requestReply()
  }

  // 底部固定层占掉的总高度，页面据此留白。
  //
  // ⚠️ 输入条**只属于聊天页**（用户否过："输入栏怎么跑到这里来了，这不应该只存在于聊天界面吗"）。
  // 所以在别的页面上，底部只有那条导航栏 —— 留白也必须跟着变小，否则页面底下会空一大截。
  val bottomInset =
    if (tab == LotusTab.Chat) LotusComposerHeight + ComposerBarGap + BarHeight + BarMargin
    else BarHeight + BarMargin + 18.dp

  // 键盘高度。**必须在两个槽外面读一次**：page 槽（聊天列表）也要用它留位。
  val density = LocalDensity.current
  val imePx = WindowInsets.ime.getBottom(density)
  val keyboardInset = with(density) { imePx.toDp() }

  GlassBackground(
    // ---- page：页面内容。会被录进 pageBackdrop，供悬浮玻璃采样（= 真正的"背景模糊"）----
    page = {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .then(dismissKeyboardOnBlank)
      ) {
        when (tab) {
          LotusTab.Chat ->
            if (inChat) {
              ChatScreen(
                messages = messages,
                bottomInset = bottomInset,
                keyboardInset = keyboardInset,
                onBack = { inChat = false },
                // 五个显示开关全部来自「设置 → 对话设置」。这里**每次重组都读一遍**
                // （SharedPreferences 有内存缓存，不贵）—— 这样从设置页切回来立刻生效。
                display =
                  ChatDisplay(
                    showModel = AppPrefs.showModel(context),
                    showUserAvatar = AppPrefs.showUserAvatar(context),
                    showTime = AppPrefs.showTime(context),
                    showTokens = AppPrefs.showTokens(context),
                    showChars = AppPrefs.showChars(context),
                  ),
                userAvatarPath = userAvatarPath,
                paintingId = paintingId,
                replyingId = replyingId,
                onOpenSources = { sourcesSheet = it },
                // 长按气泡的动作：复制在聊天页本地做，其余三个回到这里改数据。
                onQuote = { quoteMessage(it) },
                onEdit = { editMessage(it) },
                onRegenerate = { regenerate(it) },
                onDelete = { deleteMessage(it) },
              )
            } else {
              HomeScreen(
                bottomInset = bottomInset,
                assistantName = displayName,
                onArtBounds = onHomeArtBounds,
              )
            }
          // MD 工作区 = 一个文件夹：记忆和人设本来就是同一批 .md 文件，合并成一格。
          LotusTab.Files -> MdWorkspace(bottomInset = bottomInset)
          LotusTab.Settings ->
            SettingsScreen(
              providers = providers,
              onProvidersChange = { saveProviders(it) },
              selectedProviderId = selectedProviderId,
              selectedModelId = selectedModelId,
              onSelectModel = { pid, mid -> pickModel(pid, mid) },
              thinkingIndex = safeThinkingIndex,
              onThinking = { thinkingIndex = it },
              assistantName = assistantName,
              onAssistantName = { renameAssistant(it) },
              messageCount = messages.size,
              conversationCount = conversations.count { !it.archived },
              archivedConversations = conversations.filter { it.archived },
              onOpenArchived = { id -> restoreArchived(id); openConversation(id) },
              onRestoreArchived = { restoreArchived(it) },
              onPurgeArchived = { purgeArchived() },
              memoryFiles = Prompts.allMarkdown(context).size,
              onOpenMd = { tab = LotusTab.Files },
              userAvatarPath = userAvatarPath,
              onUserAvatarChange = { userAvatarPath = AvatarStore.path(context) },
              capabilities = capabilities,
              onCapabilitiesChange = {
                capabilities = it
                CapabilityStore.save(context, it)
              },
              checkingUpdate = updateChecking,
              onCheckUpdate = { runUpdateCheck() },
              onBackupImported = { reloadImportedData() },
              bottomInset = bottomInset,
            )
        }
      }
    },
    // ---- content：悬浮玻璃层。采样 combined(base, page) ----
    content = {
      // ⚠️ 必须在 GlassBackground 的 content 之内读 —— LocalGlassBackdrop 是在那一层 provide 的。
      val backdrop = LocalGlassBackdrop.current
      if (backdrop != null) {
        // 键盘高度在 LotusApp 顶部已经读过了（page 槽同样要用），这里直接用。

        val barStack = ComposerBarGap + BarHeight + BarMargin
        val barStackPx = with(density) { barStack.toPx() }

        // 0 = 没有键盘；1 = 键盘已经高过整条底栏。
        //
        // 为什么不用 `imeVisible: Boolean` 做开关：开关是**跳变**的。上一版按开关隐藏底栏，
        // 底栏是输入条的兄弟节点 —— 底栏一出现就把输入条顶上去 170dp，于是"收键盘时输入条
        // 先砸到底边、再被拉上来"。用户报的就是这个。
        // 换成这个 0..1 的连续量之后：输入条的下边距从"底栏那一条的高度"线性收到 0，
        // 再叠上 imePadding，合位移是单调的 —— 不会过冲、不会回拉。
        val imeCover = if (barStackPx > 0f) (imePx / barStackPx).coerceIn(0f, 1f) else 0f

        // 输入条的抬升量 = max(底栏那一条的高度, 键盘高度)。
        //
        // 为什么必须是"取大者"这一个表达式，而不是"imePadding + 一段动态留白"：
        // 两个机制相加时，只要两者**不是同一时刻**更新（系统在某些 ROM 上会先给最终值、
        // 再逐帧动画），合位移就会先变小再变大 —— 表现就是用户看到的"往下沉一点再弹回来"。
        // 换成 max() 之后位移是 imePx 的**单调不减函数**，数学上不存在过冲和回拉。
        //
        //  - 键盘还没高过底栏时：max = 底栏高度 → 输入条**原地不动**，底栏淡出；
        //  - 键盘高过底栏之后：max = 键盘高度 → 输入条贴着键盘走。
        val liftPx = kotlin.math.max(barStackPx, imePx.toFloat())
        val composerLift = with(density) { liftPx.toDp() }

        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
          // ⓪ 点空白关浮层的透明遮罩。
          //
          // ⚠️ 必须是这一层的**第一个子节点**（最早绘制 = 最底）。之前把它排在底栏和输入条
          // 后面，它就盖在输入条上 —— 面板开着时点 ⚡/⇄，那一下全被遮罩吃掉，只会"关掉面板"，
          // 想从模型面板直接切到档位面板得点两次。放在最底下之后：底栏、输入条、面板都在它
          // 上面，点哪儿都还是原来的按键；只有真正点在空白处才落到遮罩上。
          // ⚠️ 这里曾经写成 `composerMenu != null`。composerMenu 是 Boolean —— `!= null` 恒真，
          //    于是这块**全屏点击遮罩永远在**，它盖在 page 槽上面，把所有页面内的点击都吃掉了
          //    （症状：设置页 / MD 页整页点不动，只有底下那层 content 槽的底栏还能点）。
          //    编译器其实警告过 "Condition is always 'true'"，别再把警告当噪音。
          val anyOverlay = composerMenu || historyOpen || chatPanelOpen || attachMenu
          if (anyOverlay) {
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                  ) {
                    closeOverlays()
                    // 跟"取消键盘"同一件事：用户口径「打开输入框后点击空白地方应该要跟取消键盘
                    // 一样的操作啊，怎么点开后一直在按空白处，它没有取消呢」。
                    focus.clearFocus()
                  }
            )
          }

          // ⓪-1 聊天面板：**上下文 + 会话动作合成一块**（右上角那枚钮打开）。
          //
          // 以前是两个按钮开两个面板（一个表盘开上下文、一个 ⋯ 开动作）—— 又占地方又重复。
          // 用户口径：「更多太啰嗦了吧，而且太大了……还有上下文，你看怎么样能合并一下」。
          if (chatPanelOpen) {
            GlassChatPanel(
              usedTokens = usedTokens,
              windowTokens = contextWindow,
              real = usageIsReal,
              messageCount = messages.size,
              summaryChars = currentConversation?.summary?.length ?: 0,
              busy = compressing,
              onCompress = { compressContext() },
              rememberBusy = rememberBusy,
              rememberHint = rememberHint,
              onRemember = { rememberConversation() },
              onNewChat = { chatPanelOpen = false; newChat() },
              onRename = { chatPanelOpen = false; renaming = true },
              onDelete = { chatPanelOpen = false; deleting = true },
              onTools = { chatPanelOpen = false; toolsOpen = true },
              // 行尾小字只报"配好的外围能力有几项"，不假报常开的那些。
              toolsHint =
                listOf(
                    capabilities.ready(ai.lotus.app.data.CapabilityKind.Image),
                    capabilities.ready(ai.lotus.app.data.CapabilityKind.Video),
                    capabilities.ready(ai.lotus.app.data.CapabilityKind.Search),
                    capabilities.ready(ai.lotus.app.data.CapabilityKind.Ocr),
                  )
                  .count { it }
                  .let { "$it/4 已配" },
              modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 62.dp),
            )
          }

          // ⓪-2 会话历史浮层：左上角历史钮打开，列表是**真的**（落盘的会话）。
          if (historyOpen) {
            ConversationHistoryPanel(
              conversations = conversations.filterNot { it.archived },
              currentId = currentId,
              onOpen = { openConversation(it) },
              onNewChat = { newChat() },
              modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 62.dp),
            )
          }

          // ⓪-3 悬浮顶排 —— **它替代顶栏**。
          //
          // 用户口径：「最上方的顶栏去掉，液态玻璃就是为了放大空间代替顶栏以求更大空间的做法。」
          // 所以这两枚玻璃是**浮**在页面上的：不占布局高度，页面内容从它们底下滚过去，
          // 玻璃才有得糊、有得折（以前页面被 60dp 的顶栏挡住，内容永远碰不到玻璃）。
          if (tab == LotusTab.Chat) {
            if (inChat) {
              // 返回 = 存好这一条、回首页空态。会话已经进历史，不丢东西。
              FloatingBackButton(
                onClick = { newChat() },
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 6.dp),
              )
              // 一枚玻璃钮：外圈是用量，中间是 ⋯。点它开 [GlassChatPanel]。
              FloatingMoreGauge(
                ratio = if (contextWindow > 0) usedTokens.toFloat() / contextWindow else 0f,
                onClick = {
                  chatPanelOpen = !chatPanelOpen
                  historyOpen = false
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 6.dp),
              )
            } else {
              FloatingHistoryButton(
                onClick = {
                  historyOpen = !historyOpen
                  chatPanelOpen = false
                },
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 6.dp),
              )
            }
          }

          // ⓪-6 自我改写回执：模型真的往自己的提示词文件里写了东西时，浮一小条告诉用户。
          //      不说的话用户根本不知道它有没有写进去 —— 这正是之前最招骂的那个 bug。
          if (writeHint.isNotBlank()) {
            LaunchedEffect(writeHint) {
              delay(4200)
              writeHint = ""
            }
            Text(
              text = writeHint,
              color = LotusPalette.Ink,
              fontSize = 12.sp,
              modifier =
                Modifier.align(Alignment.TopCenter)
                  .padding(top = 58.dp)
                  .glassPanel(
                    shape = RoundedCornerShape(50),
                    strength = 0.5f,
                    tint = Color.White.copy(alpha = 0.34f),
                    rimAlpha = 0.7f,
                    elevation = 10.dp,
                  )
                  .padding(horizontal = 14.dp, vertical = 7.dp),
            )
          }

          // ① 底栏：锚死在窗口底部，**永远不动**（键盘盖上来时淡出）。
          LotusBottomBar(
            activeKey = tab.key,
            onSelect = { key ->
              composerMenu = false
              LotusTab.entries.firstOrNull { it.key == key }?.let { tab = it }
            },
            backdrop = backdrop,
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .padding(bottom = BarMargin)
                .width(BarWidth)
                .graphicsLayer { alpha = 1f - imeCover },
          )

          // ② 输入条 + 上方浮层 —— **只在聊天页**。
          //    别的页面（记忆/人设/设置）底部只有那条导航栏，输入条不该出现在那里。
          if (tab == LotusTab.Chat) {
            // ② 输入条：跟着键盘抬（imePadding），落回时停到底栏正上方（composerLift）。
            LotusComposer(
              value = draft,
              onValueChange = { draft = it },
              // 「+」不再直接开相册：先给图片 / 视频 / 文件三选一。
              onAttach = {
                composerMenu = false
                attachMenu = !attachMenu
              },
              // ⚡ + ⇄ 合成**一块**玻璃片（用户：把闪电标和模型合并，上面选模型下面选思考等级）。
              onOpenPanel = { composerMenu = !composerMenu },
              onSend = { send() },
              onHeightChange = { composerHeight = it },
              // 显示名优先用提供方给的原始 id（gpt-6-luna 这种），认不出就原样显示。
              modelLabel = selectedModelId,
              thinkingLabel = currentThinking.getOrNull(safeThinkingIndex)?.label.orEmpty(),
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp)
                  // offset 是**布局之后**的平移，不参与父级约束，所以这里不可能再反过来影响排版。
                  .offset(y = -composerLift),
            )

            // ③ 输入条上方的浮层：附件缩略片 + 思考/模型玻璃面板。
            //    它们和输入条用同一个抬升量，所以键盘弹起时一起走。
            val aboveComposer = composerLift + composerHeight + 12.dp

            // 「+」的小菜单：一块玻璃，**竖着排**的三行（图片 / 视频 / 文件）。
            if (attachMenu) {
              ai.lotus.app.ui.home.GlassAttachMenu(
                onPickImage = {
                  attachMenu = false
                  pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                onPickVideo = {
                  attachMenu = false
                  pickVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                  )
                },
                onPickFile = {
                  attachMenu = false
                  pickDoc.launch(arrayOf("*/*"))
                },
                modifier =
                  Modifier.align(Alignment.BottomStart)
                    .offset(y = -aboveComposer)
                    .padding(start = 18.dp),
              )
            }

            // 视频 / 文件的回执片：只显示名字和大小，能点掉。
            attachDoc?.let { d ->
              DocChip(
                name = d.name,
                size = ai.lotus.app.data.humanBytes(d.bytes),
                onRemove = { attachDoc = null },
                modifier =
                  Modifier.align(Alignment.BottomStart)
                    .offset(y = -aboveComposer - (if (attachmentPath != null) 52.dp else 0.dp))
                    .padding(start = 22.dp),
              )
            }

            attachmentPath?.let { path ->
              AttachmentChip(
                uri = Uri.fromFile(java.io.File(path)),
                onRemove = { attachmentPath = null },
                modifier =
                  Modifier.align(Alignment.BottomStart)
                    .offset(y = -aboveComposer)
                    .padding(start = 22.dp),
              )
            }

            // ⚠️ 面板的开/关都要走动画：进场淡入上浮，退场**往下滑**淡出。
            // 用户口径：「我点旁边空白，它应该自己往下滑，它却不会消失」——
            // 原来 `composerMenu?.let {}` 是从组合里直接消失，硬切、没有下滑。
            // 退场那一帧 composerMenu 已经是 null，所以要靠 `expandedMenu` 记住关的是哪个面板。
            AnimatedVisibility(
              visible = composerMenu,
              enter =
                fadeIn(tween(160)) +
                  slideInVertically(tween(220), initialOffsetY = { it / 5 }),
              exit =
                fadeOut(tween(130)) +
                  slideOutVertically(tween(190), targetOffsetY = { it / 3 }),
              modifier = Modifier.align(Alignment.BottomCenter).offset(y = -aboveComposer),
            ) {
              // 一块玻璃、上下两段：上「模型」下「思考」。
              val groups =
                providers
                  .filter { it.models.isNotEmpty() }
                  .map { ModelGroup(it.id, it.name.ifBlank { "未命名提供方" }, it.models) }
              GlassComposerPanel(
                groups = groups,
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId,
                thinking = currentThinking,
                thinkingIndex = safeThinkingIndex,
                hint = thinkingHint,
                // ⚠️ 选完**不关面板**：用户口径「为啥每次选完之后就直接关闭页面了？不应该啊，
                // 万一还要选思考强度那些呢」—— 模型和思考在同一个面板里，选完模型接着选档位，
                // 关面板只认"点面板外"和"再点一次那块玻璃片"。
                onPickModel = { pid, mid -> pickModel(pid, mid) },
                onPickThinking = { index -> thinkingIndex = index },
              )
            }
          }

          // ⓪-4 两个模态玻璃弹层：改名字 / 删会话。放最后 = 画在最上层。
          if (renaming) {
            val conv = conversations.firstOrNull { it.id == currentId }
            GlassPromptDialog(
              title = "修改聊天名称",
              initial =
                conv?.title?.takeIf { it.isNotBlank() } ?: conv?.displayTitle.orEmpty(),
              confirmLabel = "保存",
              onConfirm = { renameConversation(it) },
              onDismiss = { renaming = false },
            )
          }
          if (deleting) {
            GlassConfirmDialog(
              title = "删除这个会话？",
              body = "会移到「设置 → 已归档」，随时能恢复，不是真的删除。",
              confirmLabel = "删除",
              onConfirm = { deleteConversation() },
              onDismiss = { deleting = false },
            )
          }

          // ⓪-4a 来源列表 → 应用内阅读器。
          sourcesSheet?.let { urls ->
            GlassSourcesSheet(
              urls = urls,
              onOpen = { url ->
                sourcesSheet = null
                readerUrl = url
              },
              onDismiss = { sourcesSheet = null },
            )
          }

          // ⓪-4a 应用内阅读器：点消息底部的来源 → 在这里打开它读过的页面。
          readerUrl?.let { url ->
            ai.lotus.app.ui.reader.ReaderScreen(url = url, onClose = { readerUrl = null })
          }

          // ⓪-4b 「工具」那一页。
          if (toolsOpen) {
            GlassToolsPanel(
              webSearch = AppPrefs.webSearchEnabled(context),
              searchReady = capabilities.ready(ai.lotus.app.data.CapabilityKind.Search),
              imageReady = capabilities.ready(ai.lotus.app.data.CapabilityKind.Image),
              videoReady = capabilities.ready(ai.lotus.app.data.CapabilityKind.Video),
              ocrReady = capabilities.ready(ai.lotus.app.data.CapabilityKind.Ocr),
              onDismiss = { toolsOpen = false },
            )
          }

          // 保存 / 归档的轻量确认。放在内容层，能真实采样并模糊页面。
          AnimatedVisibility(
            visible = transientToast != null,
            enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(170)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 154.dp),
          ) {
            GlassToast(text = transientToast.orEmpty())
          }

          // ⓪-5 「检查更新」的结果弹层。跟上面两个一样，**放最后 = 画在最上层**，
          //      而且住在 content 槽里，玻璃能真采到设置页做背景模糊。
          if (updateChecking || updateResult != null) {
            val r = updateResult
            val appVer = BuildConfig.VERSION_NAME.substringBefore("-")
            GlassConfirmDialog(
              title =
                when (r) {
                  null -> "正在检查更新…"
                  is UpdateChecker.Result.Newer -> "发现新版本 v${r.version}"
                  UpdateChecker.Result.UpToDate -> "已是最新版本"
                  is UpdateChecker.Result.Failed -> "没检查成功"
                },
              // ⚠️ 版本 + 更新日志**无条件显示**。
              //    之前只在"发现新版本 / 已是最新"两个分支里带日志 —— 而仓库还没有 Release，
              //    检查永远是"没检查成功"，走的正好是那个不带日志的分支，于是日志一次都没露过面。
              body =
                buildString {
                  append("本机 v").append(appVer).append('\n')
                  append(ai.lotus.app.data.Changelog.currentText()).append("\n\n")
                  append(
                    when (r) {
                      null -> "正在连 GitHub 拿最新的 Release 号…"
                      is UpdateChecker.Result.Newer -> "发现新版本。点「去下载」打开 GitHub 发布页。"
                      UpdateChecker.Result.UpToDate -> "已经是最新的一版。"
                      is UpdateChecker.Result.Failed -> r.reason
                    }
                  )
                },
              confirmLabel =
                when {
                  updateChecking -> "取消检查"
                  r is UpdateChecker.Result.Newer -> "去下载"
                  else -> "好"
                },
              onConfirm = {
                if (r is UpdateChecker.Result.Newer && r.url.isNotBlank()) {
                  runCatching {
                    context.startActivity(
                      Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(r.url) }
                    )
                  }
                }
                dismissUpdate()
              },
              onDismiss = { dismissUpdate() },
            )
          }
        }
      }
    },
  )
}

/** 视频 / 文件的回执片：一块淡玻璃 + 一行字，点一下去掉。 */
@Composable
private fun DocChip(name: String, size: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
  // ⚠️ 这块片子也要走"按下＝薄折射 + 缩放"。之前只挂了个裸 clickable，按下去一点反应没有 ——
  //    正是用户说的"被遗忘的按键"。
  val rp = ai.lotus.app.ui.glass.rowPress(RoundedCornerShape(18.dp), scale = 0.97f)
  Row(
    modifier =
      modifier
        // 统一浮层玻璃配方（原来这里是白霜 0.34 —— 全 App 最后一块"白板"）。
        .floatGlass(
          shape = RoundedCornerShape(18.dp),
          strength = 0.5f,
          elevation = 10.dp,
        )
        .then(rp.modifier)
        .clickable(interactionSource = rp.interaction, indication = null) { onRemove() }
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "$name · $size",
      color = LotusPalette.Ink,
      fontSize = 12.sp,
      maxLines = 1,
      modifier = Modifier.widthIn(max = 220.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text("×", color = LotusPalette.InkMuted, fontSize = 14.sp)
  }
}
