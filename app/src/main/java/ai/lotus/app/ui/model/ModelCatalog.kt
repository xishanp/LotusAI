package ai.lotus.app.ui.model

/**
 * 一个思考档位 —— 以及它**真正会写进请求体的字段**。
 *
 * 这是"思考模式跟着模型走"的唯一落点：UI 只是这张表的投影，档位一选，
 * 发出去的参数就是这里写的东西。两种主流语义：
 *
 *  - OpenAI 兼容（gpt-5.x / o 系）：`reasoning_effort: minimal | low | medium | high`
 *  - Anthropic 系（Claude）：`thinking: { type: "enabled", budget_tokens: N }`（N ≥ 1024）
 *
 * 不是所有模型都支持思考：不支持的模型**只给一档"不思考"**，⚡ 面板里也只有这一项 ——
 * 不给假档位，这是用户点名的要求（"思考模式要跟随着模型的真实映射"）。
 */
data class ThinkingOption(
  /** 面板上显示的名字。 */
  val label: String,
  /** OpenAI 兼容的 `reasoning_effort`；null = 这个档位不传该字段。 */
  val reasoningEffort: String? = null,
  /** Anthropic 的思考预算（token）；null = 不传。 */
  val budgetTokens: Int? = null,
) {
  /** 一行说明"实际会发出去的字段"，设置页直接显示，防止 UI 与请求参数偷偷漂移。 */
  val requestSummary: String
    get() =
      when {
        reasoningEffort != null -> "reasoning_effort: $reasoningEffort"
        budgetTokens != null -> "thinking.budget_tokens: $budgetTokens"
        else -> "不带思考参数"
      }
}

/** 一个模型 + 它支持的思考档位。 */
data class ModelSpec(
  /** 发给 `/v1/chat/completions` 的 `model`。 */
  val id: String,
  /** UI 显示名。 */
  val label: String,
  /** 厂商，只用于分组显示。 */
  val vendor: String,
  /** 支持的思考档位。**至少一项**；只有一项 = 该模型不支持思考。 */
  val thinking: List<ThinkingOption>,
)

/**
 * 模型能力表。
 *
 * ⚠️ 这张表现在是**按各家 API 语义手写的**，不是从 provider 拉的 ——
 * 用户给的那把 key 在 mzhcloud 上属于 **image 分组**，`/v1/models` 只返回图像模型
 * （实测报错："No available channel for model gpt-5.2 under group image"）。
 * 拿到 chat 分组的 key 之后，用 `/v1/models` 的真实 id 覆盖这里的 `id` 即可，
 * 思考映射不动 —— 它由厂商语义决定，不由 key 决定。
 */
/**
 * OpenAI 兼容推理模型的**官方档位名**。
 *
 * 用户口径：「一般思考等级不是用英语来划分的吗？这就没必要英语汉化」——
 * 所以一律用上游/官方原词，不翻中文：`minimal / low / medium / high / xhigh`。
 *
 * `minimal` 在这条上游上**真的会把思考关掉**（gpt-6-luna 实测 reasoning_tokens = 0），
 * 所以它同时承担"不思考"的角色 —— 不另造一个 `off`。
 */
private val OpenAiEffortThinking =
  listOf(
    ThinkingOption("minimal", reasoningEffort = "minimal"),
    ThinkingOption("low", reasoningEffort = "low"),
    ThinkingOption("medium", reasoningEffort = "medium"),
    ThinkingOption("high", reasoningEffort = "high"),
  )

/** codex 这条线：官方档位到 `xhigh`。 */
private val OpenAiXEffortThinking =
  OpenAiEffortThinking + listOf(ThinkingOption("xhigh", reasoningEffort = "xhigh"))

/**
 * gpt-6 这一代（luna / sol / astra / terra / gpt-6.1-sol …）：**七档**，到 `max` 和 `ultra`。
 *
 * 用户口径：「GPT-6.1 sol 拥有 7 个思考档，最高能开到 ultra，你这只有 5 个」。
 * 所以这条线补全成 minimal / low / medium / high / xhigh / max / ultra。
 *
 * ⚠️ 本机拿 gpt-6-luna 实测：minimal 真的把思考关掉（reasoning_tokens=0），medium 15，
 *    high 23；而 `ultra` 这条上游**打回**（`Upstream request failed | 请求参数或格式不正确`）。
 *    也就是说"档位名"和"这条上游认不认"是两回事 —— 面板照官方档位给全，认不认由上游说话，
 *    发失败时 App 会自动降一档重发（见 LotusApp.send），不让用户对着报错发呆。
 */
private val NewestEffortThinking =
  OpenAiXEffortThinking +
    listOf(
      ThinkingOption("max", reasoningEffort = "max"),
      ThinkingOption("ultra", reasoningEffort = "ultra"),
    )

/** Anthropic：思考是**预算**（token 数），不是档位名，所以标签直接写 token。 */
private val AnthropicThinking =
  listOf(
    ThinkingOption("off", budgetTokens = null),
    ThinkingOption("budget 4K", budgetTokens = 4096),
    ThinkingOption("budget 16K", budgetTokens = 16384),
    ThinkingOption("budget 32K", budgetTokens = 32768),
  )

/**
 * 认不出 id 时的兜底：**两档**。
 *
 * 第一档不传任何思考字段（任何 OpenAI 兼容端点都不会因此报错），第二档才是 `medium`。
 * 不认识的模型硬套五档 = 用户说的"写死、也不知道是不是真有效"，这里不干那件事。
 */
val FallbackThinking: List<ThinkingOption> =
  listOf(
    ThinkingOption("off", reasoningEffort = null),
    ThinkingOption("medium", reasoningEffort = "medium"),
  )

/**
 * 一个模型该有哪些思考档位 —— **尽量真、认不出就说认不出**。
 *
 * 判据（按可信度从高到低）：
 *  1. 内置 [ModelCatalog] 里精确命中 id；
 *  2. 按模型族命中（`codex*` / `*claude*` / `gpt-5*` / `gpt-6-*` / `o3*` / `o4*` / `*r1*`）；
 *  3. 都不是 → [FallbackThinking]（两档 + 面板里明说这是兜底）。
 */
fun thinkingMapFor(modelId: String): ThinkingMap {
  val id = modelId.trim().lowercase()
  if (id.isEmpty()) return ThinkingMap(FallbackThinking, ThinkingSource.Fallback)

  ModelCatalog.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let {
    return ThinkingMap(it.thinking, ThinkingSource.Catalog)
  }

  val family =
    when {
      // gpt-6 这一代：七档到 ultra。
      id.contains("gpt-6") ||
        id.contains("gpt6") ||
        id.contains("luna") ||
        id.contains("sol") ||
        id.contains("astra") ||
        id.contains("terra") -> NewestEffortThinking
      // codex 线：官方档位到 xhigh。
      id.contains("codex") -> OpenAiXEffortThinking
      id.contains("claude") -> AnthropicThinking
      id.contains("deepseek") && (id.contains("r1") || id.contains("reason")) ->
        listOf(
          ThinkingOption("off", reasoningEffort = null),
          ThinkingOption("medium", reasoningEffort = "medium"),
          ThinkingOption("high", reasoningEffort = "high"),
        )
      id.contains("gpt-5") || id.startsWith("o3") || id.startsWith("o4") ->
        OpenAiEffortThinking
      else -> null
    }
  return if (family != null) ThinkingMap(family, ThinkingSource.Family)
  else ThinkingMap(FallbackThinking, ThinkingSource.Fallback)
}

/** 本机对着上游**真跑过**的档位证据（面板里用它说清"这不是摆样子"）。 */
private val ProbedModels: Map<String, String> =
  mapOf("gpt-6-luna" to "本机实测 reasoning_tokens：minimal 0 / medium 15 / high 23")

val ModelCatalog: List<ModelSpec> =
  listOf(
    ModelSpec(
      id = "gpt-5.2",
      label = "GPT-5.2",
      vendor = "OpenAI",
      thinking = OpenAiEffortThinking,
    ),
    ModelSpec(
      id = "gpt-5.2-mini",
      label = "GPT-5.2 mini",
      vendor = "OpenAI",
      thinking = OpenAiEffortThinking,
    ),
    ModelSpec(
      id = "claude-sonnet-4.5",
      label = "Claude Sonnet 4.5",
      vendor = "Anthropic",
      thinking = AnthropicThinking,
    ),
    ModelSpec(
      id = "deepseek-chat",
      label = "DeepSeek V3.2",
      vendor = "DeepSeek",
      // 纯对话模型：没有思考参数，面板里就只有这一档（不给假的档位）。
      thinking = listOf(ThinkingOption("off")),
    ),
  )

/** 该模型是否支持思考（档位多于一个才算）。 */
val ModelSpec.supportsThinking: Boolean
  get() = thinking.size > 1

/** 换模型时把旧的档位夹进新模型的范围，避免"换了模型档位却越界"。 */
fun ModelSpec.clampThinking(index: Int): Int = index.coerceIn(0, thinking.lastIndex)

/**
 * 思考档位表是**从哪来的** —— 决定面板下面那行小字怎么写。
 *
 * 用户口径：「思考等级真实上游放出来的，现在真的是写死了，也不知道是不是真有效」。
 * 所以每一档都要能说清"它到底往请求里写了什么"，而不是一个装样子的下拉框。
 */
enum class ThinkingSource {
  /** 内置目录里按模型 id 精确命中（手写、最可信）。 */
  Catalog,
  /** 按模型**族**命中（codex / claude / gpt-5.x …），用的是该族公开的 API 语义。 */
  Family,
  /** 认不出这个 id：只给"不传参"和"medium"两档，绝不假装知道更多。 */
  Fallback,
}

data class ThinkingMap(val options: List<ThinkingOption>, val source: ThinkingSource)

/** 只要档位列表（UI 大部分地方只需要这个）。 */
fun thinkingFor(modelId: String): List<ThinkingOption> = thinkingMapFor(modelId).options

/**
 * 面板最下面那行小字：**把"这一档实际会发什么"摆到明面上**。
 *
 *  - 命中族别 → 直接写请求字段（`reasoning_effort: high`）；
 *  - 认不出 → 讲清楚这是本地兜底，别让用户以为上游真给了这些档位；
 *  - 本机实测过的模型 → 附上实测数字，证明这一档真的改变上游行为。
 */
fun thinkingHint(modelId: String, option: ThinkingOption?): String {
  val map = thinkingMapFor(modelId)
  val body =
    when (val o = option) {
      null -> "点一档试试"
      else -> "实际发送：${o.requestSummary}"
    }
  val note =
    when (map.source) {
      ThinkingSource.Fallback -> "上游没公布「$modelId」的思考档位，这里是本地兜底"
      else -> ProbedModels[modelId.trim().lowercase()]
    }
  return if (note.isNullOrBlank()) body else "$body　｜　$note"
}
