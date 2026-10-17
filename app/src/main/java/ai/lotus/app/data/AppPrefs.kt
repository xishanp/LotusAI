package ai.lotus.app.data

import android.content.Context

/**
 * App 级的小设置。
 *
 * 只放"短、少、随时要读"的东西：助手名字、当前选中的提供方/模型（见 [SelectionStore]）。
 * 会话正文那类大块数据走 json 文件，不放这里。
 */
object AppPrefs {
  private const val FileName = "app_prefs"
  private const val KeyAssistantName = "assistantName"
  private const val KeyPromptEnabled = "promptEnabled"
  private const val KeyMemoryEnabled = "memoryEnabled"
  private const val KeyWebSearch = "webSearchEnabled"
  private const val KeyAutoMemory = "autoMemoryEnabled"
  private const val KeyFullMemory = "fullMemoryEnabled"
  private const val KeyShowModel = "showModel"
  private const val KeyShowUserAvatar = "showUserAvatar"
  private const val KeyShowTime = "showTime"
  private const val KeyShowTokens = "showTokens"
  private const val KeyShowChars = "showChars"
  private const val KeyOnboardingCompleted = "onboardingCompleted"

  /** 助手显示名。空 = 用户清空了输入框，显示时回退到 [DefaultAssistantName]。 */
  fun assistantName(context: Context): String =
    context.getSharedPreferences(FileName, Context.MODE_PRIVATE).getString(KeyAssistantName, "").orEmpty()

  fun setAssistantName(context: Context, value: String) {
    prefs(context).edit().putString(KeyAssistantName, value).apply()
  }

  /**
   * 是否完成首次启动初始化。
   *
   * 只在初始化真的写入本机配置后置 true。用户之后清应用数据，会重新走一遍；
   * 不把它藏进 providers.json —— 否则删提供方时会把“已完成”一起弄丢。
   */
  fun onboardingCompleted(context: Context): Boolean =
    prefs(context).getBoolean(KeyOnboardingCompleted, false)

  fun setOnboardingCompleted(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyOnboardingCompleted, value).apply()
  }

  // ---- 提示词 / 记忆：开不开、带多少 ----
  //
  // 默认都开：真没写内容时 [Prompts.systemBlocks] 一条都不拼，所以"开"不等于"花钱"。
  // 只有在用户往 md 里写了东西之后，这两项才真正影响每轮请求的体积。

  fun promptEnabled(context: Context): Boolean = prefs(context).getBoolean(KeyPromptEnabled, true)

  fun setPromptEnabled(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyPromptEnabled, value).apply()
  }

  fun memoryEnabled(context: Context): Boolean = prefs(context).getBoolean(KeyMemoryEnabled, true)

  fun setMemoryEnabled(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyMemoryEnabled, value).apply()
  }

  // ⚠️ 2026-10-16 删掉了「默认每次最多带最近几条消息」（= 裁剪上下文）。
  //    用户口径：「裁剪那个功能没用…裁缺不如直接开个新的聊天框」。请求里从此永远带全部消息。

  /**
   * 联网搜索开关。**默认关**。
   *
   * 它不是"每轮都搜"：开了之后每次请求都会带 `web_search` 声明，由上游决定这一问要不要搜。
   * 一旦真搜了，那次请求的输入会涨到几千 token —— 所以默认关，用户要用才开。
   */
  fun webSearchEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KeyWebSearch, false)

  fun setWebSearchEnabled(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyWebSearch, value).apply()
  }

  /**
   * 自动记忆开关。**默认开**。
   *
   * 开着时：模型可以在回复里带一行隐藏标记，客户端把它摘出来追加进 `MEMORY.md`（见
   * [Prompts.MemoryProtocol] / [Prompts.splitMemos]）。**不额外发请求** —— 还是那一次调用，
   * 只是把"要记的事"随回答一起带回来。
   *
   * 关掉后：协议不再注入，模型不会写标记；手动那个「记住这段」按钮**照旧可用**。
   */
  /**
   * 「全量记忆」：每轮把 IDENTITY / SOUL / AGENTS / USER / MEMORY 全文都带上。
   *
   * ⚠️ 2026-10-16 起**恒定开启**，不再是开关。
   * 用户口径：「以后不要热插拔了，就走全量，给全部提示词。这样才能让他看到记忆。」
   * 所以这里恒为 true —— 热指针 / `<!--recall-->` 按需检索那条路已废弃，不再走。
   */
  fun fullMemoryEnabled(context: Context): Boolean = true

  fun autoMemoryEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KeyAutoMemory, true)

  fun setAutoMemoryEnabled(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyAutoMemory, value).apply()
  }

  // ---- 聊天页显示哪些附加信息（设置 → 对话设置）----
  //
  // 默认：模型 / 用户头像 / 时间 三样开着，token 和字数关着 —— 后两项是给自己算账用的，
  // 每条消息下面都挂一串数字太吵。用户随时能自己开。

  fun showModel(context: Context): Boolean = prefs(context).getBoolean(KeyShowModel, true)

  fun setShowModel(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyShowModel, value).apply()
  }

  fun showUserAvatar(context: Context): Boolean =
    prefs(context).getBoolean(KeyShowUserAvatar, true)

  fun setShowUserAvatar(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyShowUserAvatar, value).apply()
  }

  fun showTime(context: Context): Boolean = prefs(context).getBoolean(KeyShowTime, true)

  fun setShowTime(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyShowTime, value).apply()
  }

  fun showTokens(context: Context): Boolean = prefs(context).getBoolean(KeyShowTokens, false)

  fun setShowTokens(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyShowTokens, value).apply()
  }

  fun showChars(context: Context): Boolean = prefs(context).getBoolean(KeyShowChars, false)

  fun setShowChars(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean(KeyShowChars, value).apply()
  }

  private fun prefs(context: Context) =
    context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
}

/** 没设名字时用的名字。 */
const val DefaultAssistantName: String = "Lotus"

/** 开场白。**只在这一个地方生成** —— 改名时靠它把已经写出去的那句认回来并同步。 */
fun openingLine(name: String): String = "你好，我是 $name。今天想聊点什么？"

/** 认出"这是开场白，不是用户真的在问话"。改名时要按它去替换，所以别用宽松匹配。 */
private val OpeningRegex = Regex("^你好，我是 .{0,40}。今天想聊点什么？$")

fun isOpeningLine(text: String): Boolean = OpeningRegex.matches(text)
