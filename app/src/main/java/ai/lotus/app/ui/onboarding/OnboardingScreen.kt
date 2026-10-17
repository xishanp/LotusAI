package ai.lotus.app.ui.onboarding

import ai.lotus.app.R
import ai.lotus.app.data.AppPrefs
import ai.lotus.app.data.ChatEvent
import ai.lotus.app.data.ChatTurn
import ai.lotus.app.data.chatStream
import ai.lotus.app.data.DefaultContextWindow
import ai.lotus.app.data.LlmApi
import ai.lotus.app.data.Probe
import ai.lotus.app.data.Prompts
import ai.lotus.app.data.ProviderConfig
import ai.lotus.app.data.ProviderStore
import ai.lotus.app.data.SelectionStore
import ai.lotus.app.ui.glass.GlassBackground
import ai.lotus.app.ui.glass.GlassSurface
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.glass.pressLiquid
import ai.lotus.app.ui.home.LotusComposer
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 首次启动初始化。
 *
 * 顺序刻意是「先开始使用，再接模型，最后让 AI 自己问」：
 * 1. 欢迎页先让用户进入正常流程；
 * 2. 用户填 OpenAI 兼容接口，连接成功后进入对话；
 * 3. 小莲用真实模型询问称呼、爱好、语言和说话偏好；
 * 4. 模型通过现有隐藏标记把内容写入 USER / IDENTITY / SOUL，不额外维护一套问卷数据库。
 */
@Composable
fun LotusOnboarding(onComplete: () -> Unit = {}) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var phase by remember { mutableStateOf(OnboardingPhase.Welcome) }

  var baseUrl by remember { mutableStateOf("") }
  var apiKey by remember { mutableStateOf("") }
  var modelId by remember { mutableStateOf("") }
  var testing by remember { mutableStateOf(false) }
  var testHint by remember { mutableStateOf("") }
  var testOk by remember { mutableStateOf(false) }

  var assistantName by remember { mutableStateOf("小莲") }
  var onboardReady by remember { mutableStateOf(false) }

  val chat = remember { mutableStateListOf<OnboardMessage>() }
  var draft by remember { mutableStateOf("") }
  var thinking by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val focus = LocalFocusManager.current

  fun persistConnection() {
    val base = baseUrl.trim().trimEnd('/')
    val key = apiKey.trim()
    val mid = modelId.trim()
    if (base.isBlank() || key.isBlank() || mid.isBlank()) return

    val existing = ProviderStore.load(context)
    val p =
      ProviderConfig(
        id = UUID.randomUUID().toString(),
        name = "我的模型",
        baseUrl = base,
        apiKey = key,
        models = listOf(mid),
        contextWindow = DefaultContextWindow,
      )
    ProviderStore.save(
      context,
      existing.filterNot { it.name == p.name && it.baseUrl == p.baseUrl } + p,
    )
    SelectionStore.save(context, p.id, mid)
    AppPrefs.setAssistantName(context, assistantName.trim().ifBlank { "小莲" })

  }

  fun beginChat() {
    val base = baseUrl.trim().trimEnd('/')
    val key = apiKey.trim()
    val mid = modelId.trim()
    if (base.isBlank() || key.isBlank() || mid.isBlank()) {
      testOk = false
      testHint = "接口地址、API Key、模型 id 都要填"
      return
    }
    if (testing) return
    testing = true
    testOk = false
    testHint = "正在连接…"
    scope.launch {
      when (val r = LlmApi.listModels(base, key)) {
        is Probe.Ok -> {
          persistConnection()
          phase = OnboardingPhase.Chat
          chat.clear()
          chat +=
            OnboardMessage(
              fromUser = false,
              text = "接上了。先告诉我，你希望我怎么称呼你？",
            )
          testOk = true
          testHint = ""
          onboardReady = false
          assistantName = AppPrefs.assistantName(context).ifBlank { "小莲" }
        }
        is Probe.Err -> {
          testHint = r.message
          testOk = false
        }
      }
      testing = false
    }
  }

  fun send() {
    val text = draft.trim()
    if (text.isEmpty() || thinking || phase != OnboardingPhase.Chat) return
    draft = ""
    chat += OnboardMessage(fromUser = true, text = text)
    thinking = true
    focus.clearFocus()
    scope.launch {
      var raw = ""
      LlmApi.chatStream(
          baseUrl = baseUrl.trim().trimEnd('/'),
          apiKey = apiKey.trim(),
          model = modelId.trim(),
          turns =
            buildList {
              add(ChatTurn("system", OnboardingSystemPrompt))
              chat.forEach { add(ChatTurn(if (it.fromUser) "user" else "assistant", it.text)) }
            },
          reasoningEffort = null,
          maxTokens = 520,
        )
        .collect { event ->
          when (event) {
            is ChatEvent.Delta -> raw += event.text
            is ChatEvent.Failed -> raw = ""
            else -> Unit
          }
        }

      if (raw.isBlank()) {
        chat += OnboardMessage(false, "连接没有成功。检查一下接口、Key 和模型名，然后再发一次。")
        thinking = false
        return@launch
      }

      val d = Prompts.splitDirectives(raw)
      if (d.users.isNotEmpty()) Prompts.appendUserMemory(context, d.users)
      if (d.memos.isNotEmpty()) Prompts.appendAutoMemory(context, d.memos)
      if (d.writes.isNotEmpty()) Prompts.applyWrites(context, d.writes)
      if (d.archives.isNotEmpty()) Prompts.applyArchives(context, d.archives)
      if (d.users.isEmpty() && d.memos.isEmpty() && d.writes.isEmpty()) {
        Prompts.memoryIntentFallback(context, text)
        Prompts.writeIntentFallback(context, text)
      }

      val identityName =
        d.writes
          .firstOrNull { it.file.equals("IDENTITY.md", ignoreCase = true) }
          ?.text
          ?.let { Regex("(?:名字|name)\\s*[：:]\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(it) }
          ?.groupValues
          ?.getOrNull(1)
          ?.trim()
          ?.take(24)
      if (!identityName.isNullOrBlank()) {
        assistantName = identityName
        AppPrefs.setAssistantName(context, identityName)
      }

      if (d.onboardReady) onboardReady = true
      chat += OnboardMessage(false, d.visible.ifBlank { "记好了。" })
      thinking = false
    }
  }

  fun finish() {
    persistConnection()
    AppPrefs.setAssistantName(context, assistantName.trim().ifBlank { "小莲" })
    AppPrefs.setOnboardingCompleted(context, true)
    onComplete()
  }

  LaunchedEffect(chat.size, thinking) {
    if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
  }

  GlassBackground(
    page = { Box(Modifier.fillMaxSize()) },
    content = {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .imePadding()
            .padding(top = 18.dp, bottom = 14.dp)
      ) {
        when (phase) {
          OnboardingPhase.Welcome ->
            WelcomePane(
              onStart = { phase = OnboardingPhase.Connect },
            )
          OnboardingPhase.Connect ->
            ConnectPane(
              baseUrl = baseUrl,
              onBaseUrl = { baseUrl = it },
              apiKey = apiKey,
              onApiKey = { apiKey = it },
              modelId = modelId,
              onModelId = { modelId = it },
              testing = testing,
              testHint = testHint,
              testOk = testOk,
              onStart = ::beginChat,
            )
          OnboardingPhase.Chat ->
            ChatPane(
              messages = chat,
              listState = listState,
              draft = draft,
              onDraft = { draft = it },
              thinking = thinking,
              onboardReady = onboardReady,
              onSend = ::send,
              onFinish = ::finish,
            )
        }
      }
    },
  )
}

private enum class OnboardingPhase { Welcome, Connect, Chat }

private data class OnboardMessage(val fromUser: Boolean, val text: String)

private const val OnboardingSystemPrompt =
  """你是小莲，正在帮用户完成 Lotus AI 的首次初始化。自然、简短、直接，不要像表单。
目标：通过 2～4 轮对话了解并记住：用户希望怎么称呼、常用语言、爱好或长期偏好、希望你怎么说话。
一次只问一个问题，先回应再问下一个；不要重复已经问过的信息，不要说自己是 Agent。
每轮必须把用户新说的稳定信息用隐藏标记写回对应文件，客户端会自动落盘：
- 用户本人：<!--user: 主题：内容-->（写进 USER.md；主题固定用“称呼 / 语言 / 爱好 / 回答偏好”）
- 你的名字/身份：<!--write: IDENTITY.md | 名字：内容-->
- 你的性格/语气：<!--write: SOUL.md | 语气：内容-->
- 其他长期事实：<!--memo: 主题：内容-->
一次最多写 1 条 user、1 条 write、1 条 memo，短句，不要复述整段对话。
用户信息会合并进 USER.md 已有分区；不要另起“用户 · 自动维护”或日期小节。
当称呼、爱好/偏好、说话方式都了解后，正文说“初始化完成，已经记好了”，并在最后写 <!--onboard: ready-->。
隐藏标记不要让用户看到，不要在正文提“标记/注释/系统/文件”。"""

@Composable
private fun WelcomePane(onStart: () -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 18.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("LOTUS AI", color = LotusPalette.InkMuted, fontSize = 11.sp, letterSpacing = 2.sp)
      Spacer(Modifier.weight(1f))
      Text("01 / 02", color = LotusPalette.InkFaint, fontSize = 11.sp)
    }
    Spacer(Modifier.weight(0.75f))
    Image(
      painter = painterResource(R.drawable.onboarding_welcome),
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxWidth(0.96f),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "你好，我是小莲",
      color = LotusPalette.Ink,
      fontSize = 29.sp,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "我会先记住你，再陪你聊天。",
      color = LotusPalette.InkMuted,
      fontSize = 14.sp,
    )
    Spacer(Modifier.weight(1f))
    OnboardAction(text = "开始使用", enabled = true, onClick = onStart)
    Spacer(Modifier.height(12.dp))
    Text(
      "模型、人设和记忆都只保存在这台设备上",
      color = LotusPalette.InkFaint,
      fontSize = 11.sp,
    )
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun ConnectPane(
  baseUrl: String,
  onBaseUrl: (String) -> Unit,
  apiKey: String,
  onApiKey: (String) -> Unit,
  modelId: String,
  onModelId: (String) -> Unit,
  testing: Boolean,
  testHint: String,
  testOk: Boolean,
  onStart: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
  ) {
    Spacer(Modifier.height(46.dp))
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("STEP 01 / 02", color = LotusPalette.InkMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
      Text(
        "连接模型",
        color = LotusPalette.Ink,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "接上主模型后，小莲会自己问你几个问题。",
        color = LotusPalette.InkMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
      )
      Spacer(Modifier.height(8.dp))
      Image(
        painter = painterResource(R.drawable.onboarding_connect),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(220.dp),
      )
    }
    Spacer(Modifier.height(12.dp))

    GlassSurface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(26.dp),
      strength = 0.55f,
      rimAlpha = 0.62f,
      elevation = 10.dp,
      veil = 0.84f,
    ) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        FormField(
          label = "Base URL",
          value = baseUrl,
          placeholder = "https://…/v1",
          onChange = onBaseUrl,
        )
        FormDivider()
        FormField(
          label = "API Key",
          value = apiKey,
          placeholder = "只存本机",
          mask = true,
          onChange = onApiKey,
        )
        FormDivider()
        FormField(
          label = "主模型 id",
          value = modelId,
          placeholder = "必填，例如 deepseek-v4.1-flash",
          onChange = onModelId,
        )
      }
    }

    if (testHint.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      Text(
        text = testHint,
        color = if (testOk) LotusPalette.InkMuted else Color(0xFFB04B4B),
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 6.dp),
      )
    }

    Spacer(Modifier.height(18.dp))
    OnboardAction(
      text = if (testing) "连接中…" else "连接并开始",
      enabled = !testing,
      onClick = onStart,
    )
    Spacer(Modifier.height(10.dp))
    Text(
      "接口、Key 和模型 id 只保存在本机，不会写进代码。",
      color = LotusPalette.InkFaint,
      fontSize = 11.sp,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.height(18.dp))
  }
}

@Composable
private fun ChatPane(
  messages: List<OnboardMessage>,
  listState: androidx.compose.foundation.lazy.LazyListState,
  draft: String,
  onDraft: (String) -> Unit,
  thinking: Boolean,
  onboardReady: Boolean,
  onSend: () -> Unit,
  onFinish: () -> Unit,
) {
  Column(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().height(132.dp)) {
      Image(
        painter = painterResource(R.drawable.onboarding_chat),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.align(Alignment.CenterStart).size(118.dp),
      )
      SkipAction(
        onClick = onFinish,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 20.dp),
      )
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(messages) { message ->
        OnboardBubble(message)
      }
      if (thinking) {
        item { Text("正在回复…", color = LotusPalette.InkMuted, fontSize = 12.sp) }
      }
      if (onboardReady) {
        item {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Image(
              painter = painterResource(R.drawable.onboarding_finish),
              contentDescription = null,
              contentScale = ContentScale.Fit,
              modifier = Modifier.size(150.dp),
            )
            Spacer(Modifier.height(8.dp))
            OnboardAction(text = "进入 Lotus AI", enabled = true, onClick = onFinish)
          }
        }
      }
    }

    LotusComposer(
      value = draft,
      onValueChange = onDraft,
      placeholder = "说说你想让我怎么称呼你…",
      showAttach = false,
      showModel = false,
      onSend = onSend,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun SkipAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Box(
    modifier =
      modifier
        .width(62.dp)
        .height(32.dp)
        .pressLiquid(
          active = if (pressed) 1f else 0f,
          shape = RoundedCornerShape(16.dp),
          scale = 1.04f,
          strength = 0.68f,
        )
        .glassPanel(
          shape = RoundedCornerShape(16.dp),
          strength = 0.5f,
          rimAlpha = 0.46f,
          elevation = 6.dp,
          veil = 0.58f,
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text("跳过", color = LotusPalette.InkMuted, fontSize = 12.sp)
  }
}

@Composable
private fun OnboardBubble(message: OnboardMessage) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
  ) {
    Box(
      modifier =
        Modifier
          .padding(top = if (message.fromUser) 0.dp else 2.dp)
          .glassPanel(
            shape = RoundedCornerShape(18.dp),
            strength = 0.42f,
            rimAlpha = 0.42f,
            elevation = 4.dp,
            veil = 0.48f,
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      Text(message.text, color = LotusPalette.Ink, fontSize = 14.sp, lineHeight = 21.sp)
    }
  }
}

@Composable
private fun FormField(
  label: String,
  value: String,
  placeholder: String,
  mask: Boolean = false,
  onChange: (String) -> Unit,
) {
  Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
    Text(label, color = LotusPalette.InkMuted, fontSize = 11.sp)
    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
      if (value.isEmpty()) {
        Text(placeholder, color = LotusPalette.InkFaint, fontSize = 15.sp, maxLines = 1)
      }
      BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = LotusPalette.Ink, fontSize = 15.sp),
        cursorBrush = SolidColor(LotusPalette.Ink),
        visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun FormDivider() {
  Box(Modifier.fillMaxWidth().height(0.7.dp).background(LotusPalette.Hairline))
}

@Composable
private fun OnboardAction(text: String, enabled: Boolean, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press = pressed && enabled
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(52.dp)
        .pressLiquid(
          active = if (press) 1f else 0f,
          shape = RoundedCornerShape(26.dp),
          scale = 1.035f,
          strength = 0.78f,
        )
        .glassPanel(
          shape = RoundedCornerShape(26.dp),
          strength = 0.62f,
          rimAlpha = 0.62f,
          elevation = 9.dp,
          veil = 0.86f,
        )
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (enabled) LotusPalette.Ink else LotusPalette.InkMuted,
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
    )
  }
}
