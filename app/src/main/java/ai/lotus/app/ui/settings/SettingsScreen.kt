package ai.lotus.app.ui.settings

import ai.lotus.app.R
import ai.lotus.app.BuildConfig
import android.content.Intent
import android.net.Uri
import ai.lotus.app.data.AppPrefs
import ai.lotus.app.data.AvatarStore
import ai.lotus.app.data.BackupManager
import ai.lotus.app.data.BackupResult
import ai.lotus.app.data.Capabilities
import ai.lotus.app.data.CapabilityEndpoint
import ai.lotus.app.data.CapabilityKind
import ai.lotus.app.data.CapabilityStatus
import ai.lotus.app.data.Conversation
import ai.lotus.app.data.DefaultAssistantName
import ai.lotus.app.data.LlmApi
import ai.lotus.app.data.ModelCapabilityProfile
import ai.lotus.app.data.ModelCapabilityStore
import ai.lotus.app.data.ModelIdentityStatus
import ai.lotus.app.data.probeModelCapabilities
import ai.lotus.app.data.Prompts
import ai.lotus.app.data.UpdateChecker
import ai.lotus.app.data.Probe
import ai.lotus.app.data.DefaultContextWindow
import ai.lotus.app.data.ProviderConfig
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.glass.pressLiquid
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.home.GlassConfirmDialog
import ai.lotus.app.ui.home.GlassPromptDialog
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.model.thinkingFor
import ai.lotus.app.ui.theme.LotusPalette
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 设置 —— 一个大类列表 + 子页。用户口径："不应该是一个大类，其中有模型提供方 / 默认模型 /
 * 联网搜索 / 记忆 / 对话设置 / 已归档 / 常规设置 / 关于"。
 *
 * ⚠️ 没接上后端的功能**不摆能拨的开关装样子**，统一用「未接入」标出来。
 */
private enum class SettingsPage(val title: String) {
  Index("设置"),
  Providers("模型提供方"),
  ProviderEdit("提供方"),
  DefaultModel("默认模型"),
  CapabilityEdit("外围能力"),
  Search("联网搜索"),
  // ⚠️ **不做角色扮演**（用户口径：「要不然就不要有角色扮演了，最多就改个名字，头像别改了」）。
  // 这个页面只剩"改个名字"这一件事 —— 人设/Prompt 分层那套从设置里拿掉。
  Persona("人设与提示词"),
  Memory("记忆"),
  Conversation("对话设置"),
  Data("会话与数据"),
  Archived("已归档"),
  About("关于"),

  /** 用户口径：「放在其他那里关于的下面那一位单独做一个：每发一个版本，都有日志，
   *  而且都可以查看旧版本的日志」。所以它是**独立一页**，挂在「关于」下面。 */
  Changelog("版本日志"),
}

// ⚠️ 别再写死：以前这里钉着 "2026.10.11"，版本号改了这里不跟着变，
//    关于页会一直显示老版本（用户："你没有保存"那类"按了没用"的错觉，有一半是这种）。
//    一律读 build.gradle.kts 里的 versionName，单一来源。
private val AppVersion: String = ai.lotus.app.BuildConfig.VERSION_NAME

@Composable
fun SettingsScreen(
  providers: List<ProviderConfig>,
  onProvidersChange: (List<ProviderConfig>) -> Unit,
  selectedProviderId: String?,
  selectedModelId: String,
  onSelectModel: (providerId: String?, modelId: String) -> Unit,
  thinkingIndex: Int,
  onThinking: (Int) -> Unit,
  /** 助手的显示名。**只能改名**，头像固定用立绘，不给改。 */
  assistantName: String = "Lotus",
  onAssistantName: (String) -> Unit = {},
  /** 当前会话的消息条数（会话组置顶要显示真实数字，不摆假的）。 */
  messageCount: Int = 0,
  /** 本机已经落盘的会话数（历史会话那一行显示真实数字）。 */
  conversationCount: Int = 0,
  /** 已归档的会话（真列表 —— 不再是只显示一个个数）。 */
  archivedConversations: List<Conversation> = emptyList(),
  /** 归档页点开一个会话（会顺便把它恢复出来）。 */
  onOpenArchived: (String) -> Unit = {},
  /** 恢复一个归档会话。 */
  onRestoreArchived: (String) -> Unit = {},
  /** 永久删除所有归档会话。 */
  onPurgeArchived: () -> Unit = {},
  /** 跳到 MD 工作区（底栏中间那格）—— 人设 / 记忆的唯一编辑入口。 */
  onOpenMd: () -> Unit = {},
  /** 记忆库里已落盘的 .md 个数。 */
  memoryFiles: Int = 0,
  /** 每次最多带最近几条消息（0 = 不限）。 */
  /** 用户自己传的聊天头像文件路径（AvatarStore）。null = 没传。 */
  userAvatarPath: String? = null,
  /** 换过头像之后回调，让外面把聊天页重画一遍。 */
  onUserAvatarChange: () -> Unit = {},
  /** 四类外围能力（生图/生视频/搜索源/解析器）的配置。 */
  capabilities: Capabilities = Capabilities(),
  onCapabilitiesChange: (Capabilities) -> Unit = {},
  /** 「检查更新」正在跑（只在关于页用，显示行尾文案）。 */
  checkingUpdate: Boolean = false,
  /**
   * 点「检查更新」。
   *
   * ⚠️ **弹层由外面显示**（LotusApp 的悬浮玻璃层），不在这里就地弹。原因见 `Glass.kt` 的
   * `GlassBackground`：页面槽里的玻璃只采**纯色底**（避免自采样），在这里弹出来的面板
   * 背后没有内容可糊 —— 看起来就是一块白板。只有画在 content 槽（采 `base ∪ page`）里的
   * 弹层，玻璃才是真的。
   */
  onCheckUpdate: () -> Unit = {},
  /** 备份导入成功后，让 LotusApp 把内存里的模型 / 会话 / MD 状态全部重载。 */
  onBackupImported: () -> Unit = {},
  bottomInset: Dp,
) {
  var page by remember { mutableStateOf(SettingsPage.Index) }
  // ---- 「保存成功」这类一闪而过的确认 ----
  //
  // 用户口径：「应该在屏幕中上方或下方，加一个"保存成功"之类的提示」。
  // 为什么必须有：保存是**静默**的 —— 点完没有任何反馈，人根本不知道存没存进去
  // （"按了没反应"的无力感有一半来自这里）。所以每次真的落盘都给一句话。
  // 位置放在**底部偏上**（底栏上面一点）：中上方会挡住立绘和标题，下方最不打扰。
  var toast by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(toast) {
    if (toast != null) {
      delay(1600)
      toast = null
    }
  }
  var editingId by remember { mutableStateOf<String?>(null) }
  // 换用户头像：选图 → 拷进私有目录 → 通知外面刷新。
  val avatarContext = LocalContext.current
  val pickAvatar =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null && AvatarStore.save(avatarContext, uri)) onUserAvatarChange()
    }
  var editingCap by remember { mutableStateOf<CapabilityKind?>(null) }
  val parentOf: (SettingsPage) -> SettingsPage = { p ->
    when (p) {
      SettingsPage.ProviderEdit -> SettingsPage.Providers
      SettingsPage.CapabilityEdit -> SettingsPage.DefaultModel
      SettingsPage.Changelog -> SettingsPage.About
      else -> SettingsPage.Index
    }
  }
  BackHandler(enabled = page != SettingsPage.Index) { page = parentOf(page) }
  val back: () -> Unit = { page = parentOf(page) }

  // ---- 一镜到底：页面之间做连续过渡，不再硬切 ----
  //
  // 用户原话：「我连续点了几个，最后发现它没有一镜到底，而是被挡住了，说明上面还有隐藏的顶栏」。
  // 硬切 + 每次重建滚动容器 = 看起来像"被上一个页面挡住"。改成同向滑动 + 交叉淡入：
  // 进子页从右滑入、返回从左滑入，视觉上是同一块玻璃在平移。
  Box(modifier = Modifier.fillMaxSize()) {
  AnimatedContent(
    targetState = page,
    transitionSpec = {
      val forward = targetState.ordinal >= initialState.ordinal
      val enter =
        slideInHorizontally(tween(240)) { w -> if (forward) w / 5 else -w / 5 } +
          fadeIn(tween(190))
      (enter togetherWith fadeOut(tween(120)))
    },
    label = "settingsPage",
  ) { target ->
    when (target) {
    SettingsPage.Index ->
      SettingsIndex(
        bottomInset = bottomInset,
        providers = providers,
        selectedModelId = selectedModelId,
        thinkingIndex = thinkingIndex,
        assistantName = assistantName,
        messageCount = messageCount,
        conversationCount = conversationCount,
        archivedConversations = archivedConversations,
        memoryFiles = memoryFiles,
        onOpen = { page = it },
      )
    SettingsPage.Providers ->
      ProvidersPane(
        providers = providers,
        selectedProviderId = selectedProviderId,
        bottomInset = bottomInset,
        onAdd = {
          editingId = null
          page = SettingsPage.ProviderEdit
        },
        onEdit = { id ->
          editingId = id
          page = SettingsPage.ProviderEdit
        },
        onBack = back,
      )
    SettingsPage.ProviderEdit -> {
      val editing = providers.firstOrNull { it.id == editingId }
      ProviderEditPane(
        existing = editing,
        selectedProviderId = selectedProviderId,
        selectedModelId = selectedModelId,
        bottomInset = bottomInset,
        onSave = { updated ->
          val next =
            if (editing == null) providers + updated
            else providers.map { if (it.id == updated.id) updated else it }
          onProvidersChange(next)
          editingId = updated.id
          toast = "保存成功"
        },
        onSelectModel = onSelectModel,
        onDelete =
          if (editing == null) null
          else {
            {
              onProvidersChange(providers.filterNot { it.id == editing.id })
              page = SettingsPage.Providers
            }
          },
        onBack = back,
      )
    }
    SettingsPage.DefaultModel ->
      DefaultModelPane(
        providers = providers,
        selectedProviderId = selectedProviderId,
        selectedModelId = selectedModelId,
        thinkingIndex = thinkingIndex,
        capabilities = capabilities,
        bottomInset = bottomInset,
        onSelectModel = onSelectModel,
        onThinking = onThinking,
        onEditCapability = { kind ->
          editingCap = kind
          page = SettingsPage.CapabilityEdit
        },
        onBack = back,
      )
    SettingsPage.CapabilityEdit -> {
      val kind = editingCap ?: CapabilityKind.Image
      CapabilityEditPane(
        kind = kind,
        current = capabilities.get(kind),
        bottomInset = bottomInset,
        onSave = { next ->
          onCapabilitiesChange(capabilities.set(kind, next))
          toast = "已保存"
        },
        onBack = back,
      )
    }
    SettingsPage.Search -> SearchPane(bottomInset, back)
    SettingsPage.Persona ->
      PersonaPane(assistantName, onAssistantName, bottomInset, onOpenMd, back)
    SettingsPage.Memory -> MemoryPane(bottomInset, onOpenMd, back)
    SettingsPage.Conversation ->
      ConversationPane(
        bottomInset = bottomInset,
        userAvatarPath = userAvatarPath,
        onPickAvatar = {
          pickAvatar.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onClearAvatar = {
          AvatarStore.clear(avatarContext)
          onUserAvatarChange()
        },
        onBack = back,
      )
    SettingsPage.Data ->
      DataPane(
        bottomInset = bottomInset,
        conversationCount = conversationCount,
        providerCount = providers.size,
        memoryFiles = memoryFiles,
        onBackupImported = onBackupImported,
        onBack = back,
      )
    SettingsPage.Archived ->
      ArchivedPane(
        archived = archivedConversations,
        bottomInset = bottomInset,
        onOpen = onOpenArchived,
        onRestore = onRestoreArchived,
        onPurgeAll = onPurgeArchived,
        onBack = back,
      )
    SettingsPage.About ->
      AboutPane(
        bottomInset = bottomInset,
        checking = checkingUpdate,
        onCheckUpdate = onCheckUpdate,
        onOpenChangelog = { page = SettingsPage.Changelog },
        onBack = back,
      )
    SettingsPage.Changelog -> ChangelogPane(bottomInset, back)
    }
  }

  // 「保存成功」浮层。见上面 [toast] 的说明。
  // ⚠️ 它是页面槽里的浮层，背后是纯白 —— 所以**必须自带体色**（veil 拉高），
  //    否则在白底上就是一块看不见的白板。
  AnimatedVisibility(
    visible = toast != null,
    enter = fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.92f),
    exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f),
    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 8.dp),
  ) {
    val shape = RoundedCornerShape(18.dp)
    Row(
      modifier =
        Modifier.glassPanel(
            shape = shape,
            strength = 0.6f,
            veil = 1.8f,
            rimAlpha = 0.9f,
            elevation = 10.dp,
          )
          .padding(horizontal = 16.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LotusIcon(LotusGlyph.Sparkle, LotusPalette.Ink.copy(alpha = 0.8f), Modifier.size(14.dp))
      Spacer(Modifier.width(7.dp))
      Text(text = toast.orEmpty(), color = LotusPalette.Ink, fontSize = 13.sp)
    }
  }
  }
}

// ---------------------------------------------------------------- 索引页

@Composable
private fun SettingsIndex(
  bottomInset: Dp,
  providers: List<ProviderConfig>,
  selectedModelId: String,
  thinkingIndex: Int,
  assistantName: String,
  messageCount: Int,
  conversationCount: Int,
  archivedConversations: List<Conversation>,
  memoryFiles: Int,
  onOpen: (SettingsPage) -> Unit,
) {
  val settingsContext = LocalContext.current
  val tiers = thinkingFor(selectedModelId)
  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = bottomInset + 24.dp)
        .padding(horizontal = 18.dp)
        .padding(top = 8.dp)
  ) {
    // ⚠️ 顶上放立绘（小莲举着扳手 = 拧设置那味儿），**不写"设置"这两个字** —— 排法直接照抄
    //    文件页（MD）最上面那张立绘：用户口径「你应该把它放在设置的最上面，替换掉设置[标题]，
    //    直接参考文件[页]的绘图」。
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Image(
        painter = painterResource(R.drawable.lotus_settings),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.height(150.dp),
      )
    }
    Spacer(Modifier.height(6.dp))
    // ⚠️ 模型放最前面（用户口径：「把模型那几个选项放最前面更好一点吧」）—— 进设置十次有
    //    九次是为了换提供方 / 换模型；会话管理另有底栏和聊天页左上角那枚 ☰。
    Caption("模型")
    SettingsGroup(enter = 0) {
      SettingsRow(
        "模型提供方",
        if (providers.isEmpty()) "未添加" else "${providers.size} 个",
        icon = LotusGlyph.Grid,
        onClick = { onOpen(SettingsPage.Providers) },
      )
      Hairline()
      SettingsRow(
        "默认模型",
        if (selectedModelId.isBlank()) "未选" else selectedModelId,
        // ⚠️ 用户口径：「设置那里默认模型和对话设置的图标重复了，改一个」——
        //    这一行原来和「对话设置」同用 Tune（滑杆），换成 Cube（一个方块 = 一个模型）。
        icon = LotusGlyph.Cube,
        onClick = { onOpen(SettingsPage.DefaultModel) },
      )
      Hairline()
      SettingsRow(
        "联网搜索",
        if (AppPrefs.webSearchEnabled(settingsContext)) "已开启" else "关闭",
        icon = LotusGlyph.Globe,
        onClick = { onOpen(SettingsPage.Search) },
      )
    }

    // ⚠️ 「当前会话」和「历史会话」原来是两行，而且**都跳同一个页面**（会话与数据）——
    //    用户口径：「当前会话与历史会话，最后打开的都是会话，所以它们应该合并还是干嘛？」
    //    合并成一行「会话」：行尾 = 当前会话名 · 总数，点开直接进会话历史浮层
    //    （列表里就有当前这条，能切、能重命名、能新建，跟聊天页左上角那枚 ☰ 是同一个面板）。
    Caption("会话")
    SettingsGroup(enter = 1) {
      // 用户口径：「直接把会话与数据代替到会话那个位置上」—— 这一格就是「会话与数据」，
      // 不再单开一个「会话」入口（会话列表另有聊天页左上角那枚 ☰）。
      SettingsRow(
        "会话与数据",
        "$conversationCount 个会话 · $memoryFiles 个 .md",
        icon = LotusGlyph.Doc,
        onClick = { onOpen(SettingsPage.Data) },
      )
      Hairline()
      SettingsRow(
        "已归档",
        if (archivedConversations.isEmpty()) "0 个会话"
        else "${archivedConversations.size} 个会话",
        icon = LotusGlyph.Archive,
        onClick = { onOpen(SettingsPage.Archived) },
      )
    }

    Caption("对话")
    SettingsGroup(enter = 2) {
      SettingsRow(
        "人设与提示词",
        if (assistantName.isBlank()) "未设置" else assistantName,
        icon = LotusGlyph.Person,
        onClick = { onOpen(SettingsPage.Persona) },
      )
      Hairline()
      SettingsRow(
        "记忆",
        if (memoryFiles == 0) "MD · 空" else "MD · $memoryFiles 个文件",
        icon = LotusGlyph.Clock,
        onClick = { onOpen(SettingsPage.Memory) },
      )
      Hairline()
      SettingsRow(
        "对话设置",
        // 这里以前显示「思考 · medium」。模型 / 思考档位已经收归「默认模型」页，
        // 这一行只说它自己管的事：头像、显示项。
        "头像 · 显示项",
        icon = LotusGlyph.Tune,
        onClick = { onOpen(SettingsPage.Conversation) },
      )
    }

    Caption("其他")
    SettingsGroup(enter = 3) {
      SettingsRow(
        "关于",
        "v$AppVersion",
        icon = LotusGlyph.Info,
        onClick = { onOpen(SettingsPage.About) },
      )
    }
  }
}

// ---------------------------------------------------------------- 模型提供方

@Composable
private fun ProvidersPane(
  providers: List<ProviderConfig>,
  selectedProviderId: String?,
  bottomInset: Dp,
  onAdd: () -> Unit,
  onEdit: (String) -> Unit,
  onBack: () -> Unit,
) {
  SettingsScaffold(SettingsPage.Providers.title, bottomInset, onBack) {
    if (providers.isEmpty()) {
      EmptyHint("还没有提供方。点下面的「添加提供方」，填 Base URL 和 Key。")
      Spacer(Modifier.height(14.dp))
    } else {
      SettingsGroup(enter = 0) {
        providers.forEachIndexed { index, p ->
          if (index > 0) Hairline()
          ProviderRow(
            provider = p,
            selected = p.id == selectedProviderId,
            onClick = { onEdit(p.id) },
          )
        }
      }
      Spacer(Modifier.height(14.dp))
    }
    // 参考图上那枚"＋ 添加"长条按钮（Chatbox 也是这个位置、这个形状）。
    AddPillButton(text = "添加提供方", onClick = onAdd)
  }
}

/** 提供方徽标：玻璃圆片 + 名字首字。名字空着就用一枚插头感的点。 */
@Composable
private fun ProviderBadge(provider: ProviderConfig, size: Dp = 38.dp) {
  val initial = provider.name.trim().firstOrNull()?.uppercaseChar()?.toString()
  Box(
    modifier =
      Modifier.size(size)
        .glassPanel(
          shape = CircleShape,
          strength = 0.5f,
          rimAlpha = 0.55f,
          elevation = 5.dp,
          veil = 0.75f,
        ),
    contentAlignment = Alignment.Center,
  ) {
    if (initial != null) {
      Text(
        text = initial,
        color = LotusPalette.Ink,
        style = MaterialTheme.typography.titleMedium,
      )
    } else {
      LotusIcon(LotusGlyph.Cube, LotusPalette.InkMuted, Modifier.size(17.dp))
    }
  }
}

/** 提供方列表的一行：徽标 + 名字/地址 + 状态点 + 箭头。整行带按压弹动。 */
@Composable
private fun ProviderRow(provider: ProviderConfig, selected: Boolean, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "providerRowDim",
    )
  // 状态点：拿到过模型 = 实心（可以用了）；没拿到 = 空心的（还没配好/没测）。
  val ready = provider.models.isNotEmpty()

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .pressLiquid(active = press, shape = RoundedCornerShape(16.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ProviderBadge(provider)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = provider.name.ifBlank { "未命名提供方" },
        style = MaterialTheme.typography.bodyLarge,
        color = LotusPalette.Ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = provider.baseUrl.ifBlank { "还没填地址" },
        style = MaterialTheme.typography.bodySmall,
        color = LotusPalette.InkFaint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    if (selected) {
      Box(Modifier.size(6.dp).clip(CircleShape).background(LotusPalette.Ink))
      Spacer(Modifier.width(8.dp))
    }
    Box(
      modifier =
        Modifier.size(8.dp)
          .clip(CircleShape)
          .background(
            if (ready) LotusPalette.Ink.copy(alpha = 0.75f)
            else LotusPalette.Ink.copy(alpha = 0.16f)
          )
    )
    Spacer(Modifier.width(10.dp))
    LotusIcon(LotusGlyph.Chevron, LotusPalette.InkFaint, Modifier.size(13.dp))
  }
}

/** 长条玻璃胶囊按钮（列表底部的"＋ 添加"）。 */
@Composable
private fun AddPillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) 1.03f else 1f,
      animationSpec = spring(dampingRatio = 0.42f, stiffness = 900f),
      label = "pillScale",
    )
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(50.dp)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .glassPanel(
          shape = RoundedCornerShape(25.dp),
          strength = 0.5f,
          rimAlpha = 0.55f,
          elevation = 7.dp,
          veil = 0.6f,
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LotusIcon(LotusGlyph.Add, LotusPalette.Ink, Modifier.size(16.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, color = LotusPalette.Ink, style = MaterialTheme.typography.bodyLarge)
  }
}

/** 模型区标题右边那种小胶囊动作（新建 / 重置 / 获取）。 */
@Composable
internal fun MiniPill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "miniPillPress",
    )
  Box(
    modifier =
      Modifier.height(32.dp)
        // ⚠️ 它长在 [SettingsGroup] 那块大玻璃里面 —— 玻璃叠玻璃会有多出来的边，所以：
        //    静止**完全看不见**，按下才放大 + 浮出一层薄折射（pressLiquid）。
        //    也不许用"变灰"当反馈（用户口径：「就不要弄传统的操作逻辑了」）。
        .pressLiquid(
          active = if (enabled) press else 0f,
          shape = RoundedCornerShape(16.dp),
          scale = 1.05f,
        )
        .clickable(
          interactionSource = interaction,
          indication = null,
          enabled = enabled,
          onClick = onClick,
        )
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

@Composable
private fun ProviderEditPane(
  existing: ProviderConfig?,
  selectedProviderId: String?,
  selectedModelId: String,
  bottomInset: Dp,
  onSave: (ProviderConfig) -> Unit,
  onSelectModel: (String?, String) -> Unit,
  onDelete: (() -> Unit)?,
  onBack: () -> Unit,
) {
  val draftId = remember { existing?.id ?: UUID.randomUUID().toString() }
  var name by remember { mutableStateOf(existing?.name.orEmpty()) }
  var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
  var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
  var contextWindow by remember {
    mutableStateOf((existing?.contextWindow ?: DefaultContextWindow).toString())
  }
  var models by remember { mutableStateOf(existing?.models.orEmpty()) }
  var addingModel by remember { mutableStateOf(false) }
  var newModelId by remember { mutableStateOf("") }

  // 测试状态。idle = 没测 / 正在测 / 出结果，三种都在这里。
  var testing by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<String?>(null) }
  var ok by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  fun snapshot(err: String? = null, nextModels: List<String> = models) =
    ProviderConfig(
      id = draftId,
      name = name,
      baseUrl = baseUrl,
      apiKey = apiKey,
      models = nextModels,
      contextWindow = contextWindow.trim().toIntOrNull()?.takeIf { it > 0 } ?: DefaultContextWindow,
      lastError = err,
      lastTestedAt = if (err == null && nextModels.isNotEmpty()) System.currentTimeMillis()
      else existing?.lastTestedAt ?: 0L,
    )

  /** 点「检查 / 获取」都走这里：请求 {Base URL}/models，一次验证地址、key、和模型清单。 */
  fun runTest() {
    if (testing) return
    testing = true
    result = null
    onSave(snapshot())
    scope.launch {
      when (val r = LlmApi.listModels(baseUrl, apiKey)) {
        is Probe.Ok -> {
          ok = true
          result = "通了 · 拿到 ${r.models.size} 个模型"
          models = r.models
          onSave(snapshot(err = null, nextModels = r.models))
        }
        is Probe.Err -> {
          ok = false
          result = r.message
          models = emptyList()
          onSave(snapshot(err = r.message, nextModels = emptyList()))
        }
      }
      testing = false
    }
  }

  SettingsScaffold(SettingsPage.ProviderEdit.title, bottomInset, onBack) {
    GlassField("名称", name, "自己起个名（可留空）") { name = it }
    Spacer(Modifier.height(10.dp))
    GlassField("Base URL", baseUrl, "https://…/v1") { baseUrl = it }
    if (baseUrl.isNotBlank()) {
      // 预览一下真正会打到的两个地址 —— 地址填错是这类配置最常见的坑。
      Text(
        text = "预览：${endpointPreview(baseUrl)}",
        style = MaterialTheme.typography.bodySmall,
        color = LotusPalette.InkFaint,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
      )
    }
    Spacer(Modifier.height(10.dp))
    GlassField(
      "API Key",
      apiKey,
      "sk-…",
      mask = true,
      trailing = {
        MiniPill(text = if (testing) "检查中" else "检查", enabled = !testing) { runTest() }
      },
    ) { apiKey = it }
    Spacer(Modifier.height(10.dp))
    GlassField("上下文窗口", contextWindow, "128000", digitOnly = true) { contextWindow = it }

    result?.let {
      Spacer(Modifier.height(12.dp))
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) LotusPalette.Ink else LotusPalette.InkMuted,
        modifier = Modifier.padding(horizontal = 6.dp),
      )
    }

    Spacer(Modifier.height(18.dp))

    // ---- 模型 ----
    // 参考图（Chatbox）里模型就挂在提供方下面：标题 + 新建/重置/获取 + 每行一个减号。
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("模型", style = MaterialTheme.typography.titleMedium, color = LotusPalette.Ink)
      Spacer(Modifier.weight(1f))
      MiniPill(text = "新建", enabled = !testing) {
        addingModel = !addingModel
        newModelId = ""
      }
      Spacer(Modifier.width(8.dp))
      MiniPill(text = "重置", enabled = models.isNotEmpty()) {
        models = emptyList()
        onSave(snapshot(nextModels = emptyList()))
      }
      Spacer(Modifier.width(8.dp))
      MiniPill(text = if (testing) "获取中" else "获取", enabled = !testing) { runTest() }
    }

    if (addingModel) {
      Spacer(Modifier.height(10.dp))
      GlassField("新模型 id", newModelId, "手动填一个，比如 gpt-5") { newModelId = it }
      Spacer(Modifier.height(8.dp))
      Row {
        MiniPill(
          text = "添加",
          enabled = newModelId.isNotBlank(),
        ) {
          val id = newModelId.trim()
          if (id.isNotEmpty() && id !in models) {
            models = models + id
            onSave(snapshot(nextModels = models))
          }
          addingModel = false
          newModelId = ""
        }
        Spacer(Modifier.width(8.dp))
        MiniPill(text = "取消") {
          addingModel = false
          newModelId = ""
        }
      }
    }

    Spacer(Modifier.height(10.dp))
    if (models.isEmpty()) {
      EmptyHint("还没有模型。填好地址和 key，点一次「获取」。")
    } else {
      SettingsGroup(enter = 0) {
        models.forEachIndexed { index, id ->
          if (index > 0) Hairline()
          ModelRow(
            id = id,
            selected = selectedProviderId == draftId && selectedModelId == id,
            onClick = { onSelectModel(draftId, id) },
            onRemove = {
              models = models - id
              onSave(snapshot(nextModels = models))
            },
          )
        }
      }
    }

    Spacer(Modifier.height(18.dp))
    SettingsGroup(enter = 1) {
      SettingsRow("保存", null, chevron = false, strong = true, onClick = { onSave(snapshot()) })
    }

    onDelete?.let {
      Spacer(Modifier.height(12.dp))
      SettingsGroup(enter = 2) {
        SettingsRow("删除这个提供方", null, chevron = false, onClick = it)
      }
    }
  }
}

/** 把 Base URL 补成真正会请求的地址，给用户看一眼。 */
private fun endpointPreview(baseUrl: String): String {
  val base = baseUrl.trim().trimEnd('/')
  return if (base.isEmpty()) "" else "$base/chat/completions"
}

/** 一行模型：点它 = 选成当前模型；右边的减号 = 从这个提供方里去掉。 */
@Composable
private fun ModelRow(
  id: String,
  selected: Boolean,
  onClick: () -> Unit,
  onRemove: () -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "modelRowDim",
    )
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .pressLiquid(active = press, shape = RoundedCornerShape(16.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = id,
      style = MaterialTheme.typography.bodyLarge,
      color = LotusPalette.Ink,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Text(
        text = "当前",
        style = MaterialTheme.typography.bodySmall,
        color = LotusPalette.InkMuted,
        modifier = Modifier.padding(end = 10.dp),
      )
    }
    // 减号：玻璃圆片 + 一根横线（不引 material 图标，省 60MB dex）。
    val removeInteraction = remember { MutableInteractionSource() }
    val removePressed by removeInteraction.collectIsPressedAsState()
    val removePress by
      animateFloatAsState(
        targetValue = if (removePressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "removePress",
      )
    Box(
      modifier =
        Modifier.size(28.dp)
          // 不描边、不变灰：静止隐形，按下才浮出玻璃（pressLiquid）。
          .pressLiquid(active = removePress, shape = CircleShape, scale = 1.1f)
          .clickable(interactionSource = removeInteraction, indication = null) { onRemove() },
      contentAlignment = Alignment.Center,
    ) {
      Box(
        Modifier.width(11.dp).height(1.6.dp).background(LotusPalette.Ink.copy(alpha = 0.8f))
      )
    }
  }
}

// ---------------------------------------------------------------- 默认模型

@Composable
private fun DefaultModelPane(
  providers: List<ProviderConfig>,
  selectedProviderId: String?,
  selectedModelId: String,
  thinkingIndex: Int,
  capabilities: Capabilities,
  bottomInset: Dp,
  onSelectModel: (providerId: String?, modelId: String) -> Unit,
  onThinking: (Int) -> Unit,
  onEditCapability: (CapabilityKind) -> Unit,
  onBack: () -> Unit,
) {
  val tiers = thinkingFor(selectedModelId)
  val withModels = providers.filter { it.models.isNotEmpty() }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
  var modelCapabilities by remember(selectedProviderId, selectedModelId) {
    mutableStateOf(ModelCapabilityStore.load(context, selectedProviderId.orEmpty(), selectedModelId))
  }
  var probingCapabilities by remember { mutableStateOf(false) }
  var capabilityHint by remember { mutableStateOf("") }

  fun detectCapabilities() {
    val provider = selectedProvider ?: return
    if (selectedModelId.isBlank() || probingCapabilities) return
    probingCapabilities = true
    capabilityHint = ""
    scope.launch {
      val result = probeModelCapabilities(provider, selectedModelId)
      ModelCapabilityStore.save(context, provider.id, selectedModelId, result)
      modelCapabilities = result
      capabilityHint =
        "视觉 ${result.vision.label} · 联网 ${result.nativeWebSearch.label}"
      probingCapabilities = false
    }
  }

  SettingsScaffold(SettingsPage.DefaultModel.title, bottomInset, onBack) {
    if (withModels.isEmpty()) {
      EmptyHint("还没有模型。填好地址和 key，点一次「测试连接」。")
      Spacer(Modifier.height(14.dp))
    }
    withModels.forEach { p ->
      Caption(p.name.ifBlank { "未命名提供方" })
      SettingsGroup {
        p.models.forEachIndexed { index, id ->
          if (index > 0) Hairline()
          SettingsRow(
            title = id,
            value = null,
            selected = selectedProviderId == p.id && selectedModelId == id,
            onClick = { onSelectModel(p.id, id) },
          )
        }
      }
      Spacer(Modifier.height(6.dp))
    }

    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("当前模型能力", style = MaterialTheme.typography.titleMedium, color = LotusPalette.Ink)
      }
      MiniPill(
        text = if (probingCapabilities) "检测中" else "检测能力",
        enabled = selectedProvider != null && selectedModelId.isNotBlank() && !probingCapabilities,
        onClick = { detectCapabilities() },
      )
    }
    Spacer(Modifier.height(10.dp))
    SettingsGroup {
      SettingsRow(
        title = "视觉输入",
        value = (modelCapabilities?.vision ?: CapabilityStatus.Unknown).label,
        selected = modelCapabilities?.vision == CapabilityStatus.Supported,
        chevron = false,
        onClick = { detectCapabilities() },
      )
      Hairline()
      SettingsRow(
        title = "原生联网",
        value = (modelCapabilities?.nativeWebSearch ?: CapabilityStatus.Unknown).label,
        selected = modelCapabilities?.nativeWebSearch == CapabilityStatus.Supported,
        chevron = false,
        onClick = { detectCapabilities() },
      )
      Hairline()
      SettingsRow(
        title = "模型一致性",
        value = modelCapabilities?.let {
          val state = it.identity.label
          if (it.responseModel.isBlank()) state else "$state · ${it.responseModel}"
        } ?: ModelIdentityStatus.Unknown.label,
        selected = modelCapabilities?.identity == ModelIdentityStatus.Match,
        chevron = false,
        onClick = { detectCapabilities() },
      )
      Hairline()
      SettingsRow(
        title = "生图",
        value = if (capabilities.ready(CapabilityKind.Image)) "已配置" else "未配置",
        selected = capabilities.ready(CapabilityKind.Image),
        chevron = false,
        onClick = { onEditCapability(CapabilityKind.Image) },
      )
      Hairline()
      SettingsRow(
        title = "搜索备用源",
        value = if (capabilities.ready(CapabilityKind.Search)) "已配置" else "未配置",
        selected = capabilities.ready(CapabilityKind.Search),
        chevron = false,
        onClick = { onEditCapability(CapabilityKind.Search) },
      )
    }
    if (capabilityHint.isNotBlank()) {
      Text(
        text = capabilityHint,
        style = MaterialTheme.typography.bodySmall,
        color = LotusPalette.InkMuted,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
      )
    }

    Spacer(Modifier.height(18.dp))
    Text("思考档位", style = MaterialTheme.typography.titleMedium, color = LotusPalette.Ink)
    Spacer(Modifier.height(10.dp))
    SettingsGroup {
      tiers.forEachIndexed { index, option ->
        if (index > 0) Hairline()
        SettingsRow(
          title = option.label,
          value = option.requestSummary,
          selected = index == thinkingIndex.coerceIn(0, tiers.lastIndex),
          onClick = { onThinking(index) },
        )
      }
    }
    // ---- 外围能力（生图 / 生视频 / 搜索源 / 解析器）----
    //
    // 用户口径：「很多模型都没有生图或者视频能力，可以再多添一个多模态能力，放密钥进去就可以生图
    // 生成视频」「有一些连视觉都没有的模型，需要用到的解析器也要弄一下，至少备在那里备用」
    // 「这些都可以放在一个叫默认模型里面」。
    Spacer(Modifier.height(22.dp))
    Text("外围能力", style = MaterialTheme.typography.titleMedium, color = LotusPalette.Ink)
    Spacer(Modifier.height(10.dp))
    SettingsGroup {
      CapabilityKind.entries.forEachIndexed { index, kind ->
        if (index > 0) Hairline()
        val ep = capabilities.get(kind)
        SettingsRow(
          title = kind.title,
          value = if (capabilities.ready(kind)) ep.statusText else "未配置",
          selected = capabilities.ready(kind),
          onClick = { onEditCapability(kind) },
        )
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}

/**
 * 编辑一个外围能力：服务名 / 地址 / key / 模型。
 *
 * ⚠️ 这里存下来的 key 目前只到**落盘**这一步，客户端还没有真正调用这些服务。UI 用「未配置 /
 * 已配置」如实标出，不摆假的"能拨开关"。真接上调用时，把 [CapabilityKind.hint] 那句改成
 * 实现后的说法。
 */
@Composable
private fun CapabilityEditPane(
  kind: CapabilityKind,
  current: CapabilityEndpoint,
  bottomInset: Dp,
  onSave: (CapabilityEndpoint) -> Unit,
  onBack: () -> Unit,
) {
  var provider by remember(current) { mutableStateOf(current.provider) }
  // 固定接口的服务（Tavily 这种）自动带上默认地址，用户只用填 key。
  var baseUrl by remember(current) {
    mutableStateOf(current.baseUrl.ifBlank { kind.defaultBaseUrl })
  }
  var apiKey by remember(current) { mutableStateOf(current.apiKey) }
  var model by remember(current) { mutableStateOf(current.model) }

  fun flush() = onSave(CapabilityEndpoint(provider, baseUrl, apiKey, model))

  SettingsScaffold(kind.title, bottomInset, onBack) {
    EmptyHint(kind.hint)
    Spacer(Modifier.height(14.dp))
    GlassField(
      label = "服务名",
      value = provider,
      placeholder = if (kind == CapabilityKind.Search) "tavily" else "随便填",
      onChange = { provider = it; flush() },
    )
    GlassField(
      label = "接口地址",
      value = baseUrl,
      placeholder = kind.defaultBaseUrl.ifBlank { "https://…" },
      onChange = { baseUrl = it; flush() },
    )
    GlassField(
      label = "API Key",
      value = apiKey,
      placeholder = "只存本机",
      mask = true,
      onChange = { apiKey = it; flush() },
    )
    // ⚠️ 搜索源**没有模型名**（Tavily 的 /search 只收一个 query）。这一格只在需要模型的
    //    那几类里出现，别再给搜索源套模型输入框 —— 用户已经点过名。
    if (kind.needsModel) {
      GlassField(
        label = "模型（必填）",
        value = model,
        placeholder = "比如 sora / dall-e-3 / gpt-4o",
        onChange = { model = it; flush() },
      )
    }
  }
}

// ---------------------------------------------------------------- 其它子页

@Composable
private fun SearchPane(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  var enabled by remember { mutableStateOf(AppPrefs.webSearchEnabled(context)) }

  SettingsScaffold(SettingsPage.Search.title, bottomInset, onBack) {
    SettingsGroup {
      GlassToggleRow(
        title = "联网搜索",
        value = if (enabled) "开" else "关",
        checked = enabled,
        onToggle = {
          enabled = it
          AppPrefs.setWebSearchEnabled(context, it)
        },
      )
      Hairline()
      // 搜索是**上游**做的：客户端只在请求里加一个声明，不额外发请求、不需要再配搜索 key。
      SettingsRow("搜索方式", "上游自带 · 不用另配 key", chevron = false, onClick = {})
      Hairline()
      // 把代价明写出来 —— 开了之后真搜的那一次输入会涨到几千 token。
      SettingsRow("搜索时开销", "那一次输入会变长", chevron = false, onClick = {})
    }
    Spacer(Modifier.height(14.dp))
    EmptyHint("关：每轮只有你打的字。开：需要查实时信息时上游自己搜，你这边仍只有一次调用。")
  }
}

/**
 * 人设页 —— **只放"不能写进文件"的东西**。
 *
 * ⚠️ 用户口径：「我在设置中点人设与提示词，我把人设改了，MD 文件区没有同步改，应该全部对接上啊」。
 * 根因就是**两处都在编辑同一批文件**（设置里一个编辑器、MD 工作区又一个），两边各记一份 UI 状态。
 * 现在只有一个真相来源：`md` 目录下的 .md 文件，在 MD 工作区里编辑。这一页只留助手名字 + 注入开关。
 */
@Composable
private fun PersonaPane(
  assistantName: String,
  onAssistantName: (String) -> Unit,
  bottomInset: Dp,
  onOpenMd: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  var promptEnabled by remember { mutableStateOf(AppPrefs.promptEnabled(context)) }
  val cost =
    Prompts.promptCost(context, assistantName.ifBlank { DefaultAssistantName }, promptEnabled, false)

  SettingsScaffold(SettingsPage.Persona.title, bottomInset, onBack) {
    GlassField(
      label = "助手名字",
      value = assistantName,
      placeholder = DefaultAssistantName,
      onChange = onAssistantName,
    )
    Spacer(Modifier.height(10.dp))

    SettingsGroup {
      GlassToggleRow(
        title = "每次请求带上提示词",
        value = if (promptEnabled) "${cost} 字" else "关闭",
        checked = promptEnabled,
        onToggle = {
          promptEnabled = it
          AppPrefs.setPromptEnabled(context, it)
        },
      )
      Hairline()
      SettingsRow(
        "身份 / 说话方式 / 规则",
        "在 MD 里改",
        icon = LotusGlyph.Folder,
        onClick = onOpenMd,
      )
    }
  }
}

/** 记忆页 —— 同上，只留开关；内容在 MD 工作区。 */
@Composable
private fun MemoryPane(bottomInset: Dp, onOpenMd: () -> Unit, onBack: () -> Unit) {
  val context = LocalContext.current
  var memoryEnabled by remember { mutableStateOf(AppPrefs.memoryEnabled(context)) }
  var autoMemory by remember { mutableStateOf(AppPrefs.autoMemoryEnabled(context)) }
  // ⚠️ 2026-10-16：记忆读取**恒定走全量**，不再有"全量 / 按需"两档（见 AppPrefs.fullMemoryEnabled）。
  // 用户口径：「以后不要热插拔了，就走全量，给全部提示词。这样才能让他看到记忆。」
  val injected =
    Prompts.promptCost(context, DefaultAssistantName, false, memoryEnabled, autoMemory, true)
  val files = remember(memoryEnabled) { Prompts.allMarkdown(context).size }

  SettingsScaffold(SettingsPage.Memory.title, bottomInset, onBack) {
    SettingsGroup {
      GlassToggleRow(
        title = "注入记忆",
        value = if (memoryEnabled) "每轮 $injected 字（全量）" else "关闭",
        checked = memoryEnabled,
        onToggle = {
          memoryEnabled = it
          AppPrefs.setMemoryEnabled(context, it)
        },
      )
      Hairline()
      // 自动记忆：模型在**同一次回答**里把该记的事用隐藏标记带回来，客户端摘掉并写进
      // MEMORY.md。不额外发请求 —— 开着只是多一段几十字的协议（下面那个字数已经算进去了）。
      GlassToggleRow(
        title = "自动记忆",
        value = if (autoMemory) "它可以自己记" else "只用「记住这段」",
        checked = autoMemory,
        onToggle = {
          autoMemory = it
          AppPrefs.setAutoMemoryEnabled(context, it)
        },
      )
      Hairline()
      // 不再是开关：读取恒定全量（IDENTITY / SOUL / AGENTS / USER / MEMORY 全文每轮都带）。
      // 一行说明摆在这儿，让人知道钱花在哪、为什么 —— 但改不了，因为这是定下来的产品决策。
      SettingsRow("读取方式", "全量 · 每轮都带", icon = LotusGlyph.Folder, onClick = onOpenMd)
      Hairline()
      SettingsRow("记忆文件", "$files 个 .md", icon = LotusGlyph.Folder, onClick = onOpenMd)
    }
  }
}

@Composable
private fun ConversationPane(
  bottomInset: Dp,
  userAvatarPath: String?,
  onPickAvatar: () -> Unit,
  onClearAvatar: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current

  // 「聊天页显示什么」五个开关。默认：模型 / 头像 / 时间 开，token / 字数 关。
  var showModel by remember { mutableStateOf(AppPrefs.showModel(context)) }
  var showAvatar by remember { mutableStateOf(AppPrefs.showUserAvatar(context)) }
  var showTime by remember { mutableStateOf(AppPrefs.showTime(context)) }
  var showTokens by remember { mutableStateOf(AppPrefs.showTokens(context)) }
  var showChars by remember { mutableStateOf(AppPrefs.showChars(context)) }

  SettingsScaffold(SettingsPage.Conversation.title, bottomInset, onBack) {
    // ⚠️ 这里以前还摆了一组「当前模型 / 思考档位 / 发出去的字段」—— 跟「默认模型」页完全重复，删掉。
    //    用户口径：「默认模型和对话设置重复了呀」。模型 / 思考档位的唯一入口 = 默认模型页。

    // ---- 用户头像：上传 / 移除（用户口径：「对话聊天那还得加一个上传用户头像」）----
    Caption("用户头像")
    SettingsGroup(enter = 1) {
      SettingsRow(
        title = "上传头像",
        value = if (userAvatarPath != null) "换一张" else "从相册选",
        icon = LotusGlyph.Person,
        onClick = onPickAvatar,
      )
      if (userAvatarPath != null) {
        Hairline()
        SettingsRow(title = "移除头像", chevron = false, onClick = onClearAvatar)
      }
    }
    Spacer(Modifier.height(12.dp))

    // ---- 聊天页显示什么：用户点名要的五项 ----
    Caption("聊天页显示")
    SettingsGroup(enter = 1) {
      GlassToggleRow(
        title = "显示模型",
        value = if (showModel) "每条助手消息下" else "关闭",
        checked = showModel,
        onToggle = {
          showModel = it
          AppPrefs.setShowModel(context, it)
        },
      )
      Hairline()
      GlassToggleRow(
        title = "用户头像",
        value = if (showAvatar) "显示" else "隐藏",
        checked = showAvatar,
        onToggle = {
          showAvatar = it
          AppPrefs.setShowUserAvatar(context, it)
        },
      )
      Hairline()
      GlassToggleRow(
        title = "完成时间",
        value = if (showTime) "HH:mm" else "关闭",
        checked = showTime,
        onToggle = {
          showTime = it
          AppPrefs.setShowTime(context, it)
        },
      )
      Hairline()
      GlassToggleRow(
        title = "耗费的 token",
        value = if (showTokens) "显示" else "关闭",
        checked = showTokens,
        onToggle = {
          showTokens = it
          AppPrefs.setShowTokens(context, it)
        },
      )
      Hairline()
      GlassToggleRow(
        title = "文字数量",
        value = if (showChars) "显示" else "关闭",
        checked = showChars,
        onToggle = {
          showChars = it
          AppPrefs.setShowChars(context, it)
        },
      )
    }
    // ⚠️ 这里原来还有「每次最多带最近几条消息（0 = 不限）」一行 —— 那就是"裁剪上下文"。
    //    用户口径：「裁剪那个功能没用…把那裁剪给删除吧」。这条旋钮连同聊天页 ⋯ 里的「裁剪」一起删了，
    //    不留一个按不动 / 改了也看不出来的死开关。
    //
    // ⚠️ 这里原来还有「流式输出 / 一次提问 / 自动压缩」三行 —— 它们长得像按键却没有 onClick，
    //    按下去什么都不发生。用户口径：「有些按键根本按不动，你还要硬给它加上去…你可以把它删掉呀」。
    //    这三条是实现事实（不是设置项），已经写在 CAPABILITIES.md 里给人看，不摆在这里装开关。
  }
}

private enum class BackupAction { Export, Import }

@Composable
private fun DataPane(
  bottomInset: Dp,
  conversationCount: Int,
  providerCount: Int,
  memoryFiles: Int,
  onBackupImported: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var promptAction by remember { mutableStateOf<BackupAction?>(null) }
  var pendingPassword by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var hint by remember { mutableStateOf("") }

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
      uri ->
      val password = pendingPassword
      pendingPassword = ""
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        busy = true
        hint = ""
        val result = BackupManager.export(context, uri, password)
        busy = false
        hint =
          when (result) {
            is BackupResult.Ok -> "已导出 ${result.files} 个文件"
            is BackupResult.Err -> result.message
          }
      }
    }

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val password = pendingPassword
      pendingPassword = ""
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        busy = true
        hint = ""
        val result = BackupManager.import(context, uri, password)
        busy = false
        when (result) {
          is BackupResult.Ok -> {
            hint = "已导入 ${result.files} 个文件"
            onBackupImported()
          }
          is BackupResult.Err -> hint = result.message
        }
      }
    }

  // ⚠️ 这里原来有一个「清空所有会话」按钮（点两次真的清空）。
  //    2026-10-11 删掉：它不在用户的需求里，是我自作主张加的，而且**已经造成过一次真实的数据丢失**——
  //    设置页长按会被 Compose 的 clickable 当成点击、自己跳进这一页，手指再动一下就落到"清空"上。
  //    会话的"删"只有一条路：聊天页 ⋯ → 删除这个会话 = **归档**（可恢复）。要清归档去「已归档」页。
  SettingsScaffold(SettingsPage.Data.title, bottomInset, onBack) {
    SettingsGroup {
      SettingsRow("会话记录", "$conversationCount 个 · conversations.json", chevron = false, onClick = {})
      Hairline()
      SettingsRow("模型提供方", "$providerCount 个 · providers.json", chevron = false, onClick = {})
      Hairline()
      SettingsRow("记忆文件", "$memoryFiles 个 .md", chevron = false, onClick = {})
    }
    Spacer(Modifier.height(12.dp))
    SettingsGroup {
      SettingsRow(
        title = "导出加密备份",
        value = if (busy) "处理中…" else "密码保护",
        onClick = { if (!busy) promptAction = BackupAction.Export },
      )
      Hairline()
      SettingsRow(
        title = "导入加密备份",
        value = if (busy) "处理中…" else "选择文件",
        onClick = { if (!busy) promptAction = BackupAction.Import },
      )
    }
    if (hint.isNotBlank()) {
      Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = if (hint.startsWith("已")) LotusPalette.Ink else Color(0xFFB14444),
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
      )
    }
  }

  promptAction?.let { action ->
    GlassPromptDialog(
      title = if (action == BackupAction.Export) "设置备份密码" else "输入备份密码",
      initial = "",
      confirmLabel = "继续",
      hint = "至少 8 位；忘记密码无法恢复。",
      maskInput = true,
      onConfirm = { password ->
        if (password.length < 8) {
          hint = "密码至少 8 位"
        } else {
          pendingPassword = password
          promptAction = null
          if (action == BackupAction.Export) {
            exportLauncher.launch("LotusAI-backup.lotusbackup")
          } else {
            importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
          }
        }
      },
      onDismiss = { promptAction = null },
    )
  }
}

/**
 * 已归档。
 *
 * ⚠️ 用户口径：「我尝试删除了一个会话，在已归档会话那里不会存在，还是没做好」——
 * 所以「删除」现在 = **归档**（软删除）：内容一条不删，随时能恢复；要真删得在这一页点。
 */
@Composable
private fun ArchivedPane(
  archived: List<Conversation>,
  bottomInset: Dp,
  onOpen: (String) -> Unit,
  onRestore: (String) -> Unit,
  onPurgeAll: () -> Unit,
  onBack: () -> Unit,
) {
  var confirming by remember { mutableStateOf(false) }

  SettingsScaffold(SettingsPage.Archived.title, bottomInset, onBack) {
    if (archived.isEmpty()) {
      EmptyHint("还没有归档的会话。删除一个会话，它会先落在这里。")
      return@SettingsScaffold
    }

    SettingsGroup {
      archived.forEachIndexed { index, c ->
        if (index > 0) Hairline()
        SettingsRow(
          title = c.displayTitle,
          value = "${c.messages.size} 条",
          chevron = true,
          onClick = { onOpen(c.id) },
        )
      }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGroup {
      SettingsRow(
        title = if (confirming) "再点一次，永久删除" else "清空归档",
        value = if (confirming) "找不回来" else "${archived.size} 个",
        chevron = false,
        onClick = {
          if (confirming) {
            onPurgeAll()
            confirming = false
          } else {
            confirming = true
          }
        },
      )
    }
  }
}

/** 一块玻璃里的一段多行 Markdown 编辑区。 */
@Composable
internal fun GlassTextArea(
  label: String,
  value: String,
  placeholder: String,
  minLines: Int,
  actionLabel: String? = null,
  onAction: () -> Unit = {},
  onChange: (String) -> Unit,
) {
  SettingsGroup {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          label,
          style = MaterialTheme.typography.bodySmall,
          color = LotusPalette.InkMuted,
          modifier = Modifier.weight(1f),
        )
        Text(
          "${value.length} 字",
          style = MaterialTheme.typography.labelSmall,
          color = LotusPalette.InkFaint,
        )
        if (actionLabel != null) {
          Spacer(Modifier.width(8.dp))
          MiniPill(actionLabel, onClick = onAction)
        }
      }
      Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (value.isEmpty()) {
          Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = LotusPalette.InkFaint)
        }
        BasicTextField(
          value = value,
          onValueChange = onChange,
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = LotusPalette.Ink),
          cursorBrush = SolidColor(LotusPalette.Ink),
          minLines = minLines,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

/** 带真开关的一行（点击整行切换）。 */
@Composable
private fun GlassToggleRow(
  title: String,
  value: String,
  checked: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  SettingsRow(
    title = title,
    value = value,
    chevron = false,
    selected = checked,
    onClick = { onToggle(!checked) },
  )
}

@Composable
private fun ChangelogPane(bottomInset: Dp, onBack: () -> Unit) {
  SettingsScaffold(SettingsPage.Changelog.title, bottomInset, onBack) {
    ai.lotus.app.data.Changelog.entries.forEach { entry ->
      SettingsGroup {
        SettingsRow(
          title = "v${entry.version}",
          value = if (entry.version == AppVersion) "当前版本" else "",
          chevron = false,
          onClick = {},
        )
        entry.lines.forEach { line ->
          Text(
            text = "· $line",
            color = LotusPalette.InkMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
          )
        }
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun AboutPane(
  bottomInset: Dp,
  checking: Boolean,
  onCheckUpdate: () -> Unit,
  onOpenChangelog: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  Box(modifier = Modifier.fillMaxSize()) {
    SettingsScaffold(SettingsPage.About.title, bottomInset, onBack) {
      // 用**两手一摊**（"为什么？"）那张立绘，透明底。用户口径：「再生成一张关于那个界面的，
        // 动作为两手一摊，就那种像是在问为什么的动作，两手向外一弹，左右两边」。
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
          painter = painterResource(R.drawable.lotus_about),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.height(150.dp),
        )
      }
      Spacer(Modifier.height(6.dp))
      SettingsGroup {
        SettingsRow("Lotus AI", "v$AppVersion", chevron = false, onClick = {})
        Hairline()
        // 用户口径：「检查更新，能做到吗？」—— 能。查 GitHub Releases 的 tag，比本机版本。
        // 有新版就开浏览器去发布页下载（App 里不偷偷装 APK）。
        SettingsRow(
          "检查更新",
          if (checking) "检查中…" else "当前 v$AppVersion",
          onClick = { if (!checking) onCheckUpdate() },
        )
        Hairline()
        // 「关于」的下面这一位：版本日志（独立一页，新旧版本都在）。
        SettingsRow("版本日志", "v$AppVersion", onClick = onOpenChangelog)
        Hairline()
        // 用户口径：「我的机器上有我的 GitHub 账号，你自己找找，找到之后添加上」——
        // 机器上 gh 的登录用户 = xishanp（戏山），已核对 GitHub API。
        SettingsRow(
          "作者 GitHub",
          "xishanp",
          chevron = true,
          onClick = {
            runCatching {
              context.startActivity(
                Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://github.com/xishanp") }
              )
            }
          },
        )
        Hairline()
        // 用户口径：「定位什么的直接给我换成作者反馈」。
        SettingsRow(
          "作者反馈",
          "g15013815279@outlook.com",
          chevron = true,
          onClick = {
            runCatching {
              context.startActivity(
                Intent(Intent.ACTION_SENDTO).apply {
                  data = Uri.parse("mailto:g15013815279@outlook.com")
                  putExtra(Intent.EXTRA_SUBJECT, "Lotus AI 反馈")
                }
              )
            }
          },
        )
      }
      // 给钉在最底下的落款留位，别被它盖住。
      Spacer(Modifier.height(170.dp))
    }

    // ⚠️ 用户口径：「把这些包括开源依赖那些话全部放到最底边，你放的位置还是太前了」。
    //    所以落款**钉死在屏幕底部**（Box 对齐，不跟着内容滚），上面才是滚动内容。
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(start = 18.dp, end = 18.dp, bottom = bottomInset + 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        "开源依赖",
        style = MaterialTheme.typography.labelSmall,
        color = LotusPalette.InkFaint,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "Kyant0 / AndroidLiquidGlass · Apache 2.0",
        style = MaterialTheme.typography.labelSmall,
        color = LotusPalette.InkFaint,
      )
      Text(
        "chrisbanes / haze · Jetpack Compose",
        style = MaterialTheme.typography.labelSmall,
        color = LotusPalette.InkFaint,
      )
    }
  }
}

// ---------------------------------------------------------------- 公共件

@Composable
internal fun SettingsScaffold(
  title: String,
  bottomInset: Dp,
  onBack: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = bottomInset + 24.dp)
        .padding(horizontal = 18.dp)
        .padding(top = 6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      val backInteraction = remember { MutableInteractionSource() }
      val backPressed by backInteraction.collectIsPressedAsState()
      val backScale by
        animateFloatAsState(
          targetValue = if (backPressed) 1.10f else 1f,
          animationSpec = spring(dampingRatio = 0.40f, stiffness = 1100f),
          label = "backScale",
        )
      Box(
        modifier =
          Modifier.size(38.dp)
            .graphicsLayer {
              scaleX = backScale
              scaleY = backScale
            }
            .glassPanel(
              shape = CircleShape,
              strength = 0.55f,
              veil = 0.9f,
              rimAlpha = 0.5f,
              elevation = 5.dp,
            )
            .clickable(
              interactionSource = backInteraction,
              indication = null,
              onClick = onBack,
            ),
        contentAlignment = Alignment.Center,
      ) {
        LotusIcon(LotusGlyph.Back, LotusPalette.Ink, Modifier.size(17.dp))
      }
      Spacer(Modifier.width(10.dp))
      Text(title, style = MaterialTheme.typography.titleLarge, color = LotusPalette.Ink)
    }
    Spacer(Modifier.height(16.dp))
    content()
  }
}

@Composable
internal fun SettingsGroup(
  /** 这一组在页面里的序号。**仅用于进场错峰**：越靠下的组晚一点点出现，读起来是"活"的。 */
  enter: Int = 0,
  content: @Composable ColumnScope.() -> Unit,
) {
  // 进场：淡入 + 上浮 10dp。第一组几乎立刻到，后面的按 45ms 依次排开。
  // 用户口径：「完全没有我们之前的 UI 语言风格，比如点到弹动那些…设置页全是死的」。
  val appeared = remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(enter * 45L)
    appeared.value = true
  }
  val enterAlpha by
    animateFloatAsState(
      targetValue = if (appeared.value) 1f else 0f,
      animationSpec = tween(durationMillis = 260),
      label = "groupAlpha",
    )
  val enterShift by
    animateFloatAsState(
      targetValue = if (appeared.value) 0f else 1f,
      animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
      label = "groupShift",
    )
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .graphicsLayer {
          alpha = enterAlpha
          translationY = enterShift * 10.dp.toPx()
        }
        .glassPanel(
          shape = RoundedCornerShape(22.dp),
          strength = 0.5f,
          rimAlpha = 0.8f,
          elevation = 8.dp,
          veil = 0.9f,
        )
        .padding(vertical = 2.dp),
    content = content,
  )
}

@Composable
internal fun Caption(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = LotusPalette.InkMuted,
    modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 8.dp),
  )
}

@Composable
internal fun Hairline() {
  Box(
    modifier =
      Modifier.fillMaxWidth().padding(start = 18.dp).height(0.7.dp).background(LotusPalette.Hairline)
  )
}

@Composable
internal fun SettingsRow(
  title: String,
  value: String? = null,
  chevron: Boolean = true,
  selected: Boolean = false,
  /** 左侧那枚玻璃圆片里的图标。null = 这一行不要图标（值行、说明行）。 */
  icon: LotusGlyph? = null,
  /**
   * 主操作行（比如"保存"）—— 按压缩放更大、玻璃更实。
   *
   * 用户口径：「点保存键的反馈感还是太弱」。普通行是"列表里的一行"，
   * 反馈必须克制；保存是**这一页唯一的动作**，反馈可以而且应该更明显。
   */
  strong: Boolean = false,
  onClick: () -> Unit,
) {
  // 按压弹动：按下整行轻微收 + 一点墨色，抬手回弹（spring 欠阻尼，带一点点余震）。
  // 用户口径：「你解释得太多了…完全没有我们之前的 UI 语言风格，比如点到弹动那些」。
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "rowDim",
    )
  Row(
    modifier =
      Modifier.fillMaxWidth()
        // ⚠️ 按下是**放大**，不是缩小（用户口径：「往内缩不好看」）。
        .pressLiquid(
          active = press,
          shape = RoundedCornerShape(16.dp),
          scale = if (strong) 1.055f else 1.03f,
          strength = if (strong) 0.95f else 0.62f,
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      // ⚠️ 这里原来给图标托了一层小玻璃片。用户 2026-10-11 定：**同一块玻璃里不许再出现第二块**
      //   （「既然都在一个玻璃下，就不应该再分出一个玻璃」）。所以图标直接裸画 —— 整行本来就
      //    坐在 [SettingsGroup] 那块大玻璃上，再垫一层只会在白底上多出一圈脏边。
      //    按键本身仍然是真的：整行的按压缩放 + 墨色反馈全在（见上面的 pressScale / pressDim）。
      Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        LotusIcon(icon, LotusPalette.Ink.copy(alpha = 0.82f), Modifier.size(17.dp))
      }
      Spacer(Modifier.width(12.dp))
    }
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      color = LotusPalette.Ink,
      modifier = Modifier.weight(1f),
    )
    if (value != null) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        color = LotusPalette.InkMuted,
        modifier = Modifier.padding(end = 8.dp),
      )
    }
    if (selected) {
      Box(Modifier.size(7.dp).clip(CircleShape).background(LotusPalette.SelectionDot))
      if (chevron) Spacer(Modifier.width(4.dp))
    }
    if (chevron) LotusIcon(LotusGlyph.Chevron, LotusPalette.InkFaint, Modifier.size(13.dp))
  }
}

@Composable
private fun GlassField(
  label: String,
  value: String,
  placeholder: String,
  mask: Boolean = false,
  /** true = 只收数字（上下文窗口那种字段，免得用户填进去一个非数字）。 */
  digitOnly: Boolean = false,
  /** 输入框右侧的插槽（比如 API Key 旁边那个「检查」）。 */
  trailing: (@Composable () -> Unit)? = null,
  onChange: (String) -> Unit,
) {
  SettingsGroup {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
      Text(label, style = MaterialTheme.typography.bodySmall, color = LotusPalette.InkMuted)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
          if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = LotusPalette.InkFaint)
          }
          BasicTextField(
            value = value,
            onValueChange = { next -> onChange(if (digitOnly) next.filter { it.isDigit() } else next) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = LotusPalette.Ink),
            cursorBrush = SolidColor(LotusPalette.Ink),
            visualTransformation =
              if (mask) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (trailing != null) {
          Spacer(Modifier.width(8.dp))
          trailing()
        }
      }
    }
  }
}

/**
 * 说明文案。
 *
 * ⚠️ 用户口径：「你太啰嗦了，整个设置页一直在解释，别搞这样子的」。
 * 所以设置页**不再渲染任何解释性段落** —— 保留这个函数只是不想动上百个调用点，
 * 它现在是个空实现。要写说明请写进子页的**标题/值**里，别再加长段落。
 */
@Composable
private fun Note(@Suppress("UNUSED_PARAMETER") text: String) {
  // 故意什么都不画。
}

/** 唯一的例外：**空状态**那一句短提示（还没配提供方 / 还没拉到模型）。 */
@Composable
private fun EmptyHint(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = LotusPalette.InkMuted,
    modifier = Modifier.padding(horizontal = 6.dp),
  )
}
