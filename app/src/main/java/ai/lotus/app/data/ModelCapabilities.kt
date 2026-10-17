package ai.lotus.app.data

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 某项能力在上游的真实状态。Unknown 不等于不支持，只表示这次没测成。 */
enum class CapabilityStatus(val label: String) {
  Supported("支持"),
  Unsupported("不支持"),
  Unknown("未检测"),
}

/** 中转站是否按请求的模型名回显。只能核对一致性，不能证明底层真伪。 */
enum class ModelIdentityStatus(val label: String) {
  Match("一致"),
  Mismatch("不一致"),
  Unknown("未回显"),
}

/**
 * 当前提供方 + 模型经过真实请求探测后的能力。
 *
 * 视觉用一张左蓝右红的测试图核对回答；原生联网要求返回当天的上海日期。
 * 检测只在用户主动点击时执行，不会在聊天时偷偷跑。
 */
@Serializable
data class ModelCapabilityProfile(
  val vision: CapabilityStatus = CapabilityStatus.Unknown,
  val nativeWebSearch: CapabilityStatus = CapabilityStatus.Unknown,
  val visionDetail: String = "",
  val webSearchDetail: String = "",
  val responseModel: String = "",
  val identity: ModelIdentityStatus = ModelIdentityStatus.Unknown,
  val checkedAt: Long = 0L,
)

object ModelCapabilityStore {
  private const val FileName = "app_prefs"
  private val json = Json { ignoreUnknownKeys = true }

  private fun key(providerId: String, modelId: String) = "model_caps|$providerId|$modelId"

  fun load(context: Context, providerId: String, modelId: String): ModelCapabilityProfile? {
    if (providerId.isBlank() || modelId.isBlank()) return null
    val raw =
      context.getSharedPreferences(FileName, Context.MODE_PRIVATE).getString(key(providerId, modelId), null)
        ?: return null
    return runCatching { json.decodeFromString<ModelCapabilityProfile>(raw) }.getOrNull()
  }

  fun save(
    context: Context,
    providerId: String,
    modelId: String,
    profile: ModelCapabilityProfile,
  ) {
    if (providerId.isBlank() || modelId.isBlank()) return
    context
      .getSharedPreferences(FileName, Context.MODE_PRIVATE)
      .edit()
      .putString(key(providerId, modelId), json.encodeToString(profile))
      .apply()
  }
}

/**
 * 发两次真实的小请求：
 * 1. 视觉：测试图右半边是红色，模型必须回答 RED；
 * 2. 原生联网：要求联网返回当天上海日期，回了正确日期才算真的能用。
 *
 * HTTP 400/404/415/422 表示上游明确拒绝；401、网络错误等记为 Unknown，不能误报成不支持。
 */
suspend fun probeModelCapabilities(provider: ProviderConfig, modelId: String): ModelCapabilityProfile {
  val vision = probe(
    provider = provider,
    modelId = modelId,
    prompt = "看图片。右半部分是什么颜色？只回复 RED 或 BLUE。",
    image = VisionTestPng,
    webSearch = false,
    maxTokens = 256,
  )
  val web = probe(
    provider = provider,
    modelId = modelId,
    prompt = "使用联网搜索，查上海今天（Asia/Shanghai）的日期。只回复 YYYY-MM-DD。",
    image = null,
    webSearch = true,
    maxTokens = 256,
  )

  val responseModel = vision.responseModel.ifBlank { web.responseModel }
  return ModelCapabilityProfile(
    vision = classifyVision(vision),
    nativeWebSearch = classifyWebSearch(web),
    visionDetail = outcomeDetail(vision, capability = "图片"),
    webSearchDetail = outcomeDetail(web, capability = "联网"),
    responseModel = responseModel,
    identity = identityStatus(modelId, responseModel),
    checkedAt = System.currentTimeMillis(),
  )
}

private data class ProbeOutcome(
  val done: Boolean,
  val text: String,
  val reasoning: String,
  val failure: String,
  val responseModel: String,
  val sources: List<String>,
)

private suspend fun probe(
  provider: ProviderConfig,
  modelId: String,
  prompt: String,
  image: String?,
  webSearch: Boolean,
  maxTokens: Int,
): ProbeOutcome {
  var done = false
  var failure = ""
  var responseModel = ""
  val text = StringBuilder()
  val reasoning = StringBuilder()
  val sources = linkedSetOf<String>()

  LlmApi
    .chatStream(
      baseUrl = provider.baseUrl,
      apiKey = provider.apiKey,
      model = modelId,
      turns = listOf(ChatTurn("user", prompt, image)),
      reasoningEffort = null,
      maxTokens = maxTokens,
      webSearch = webSearch,
    )
    .collect { event ->
      when (event) {
        is ChatEvent.ModelInfo -> if (responseModel.isBlank()) responseModel = event.model
        is ChatEvent.Delta -> text.append(event.text)
        is ChatEvent.Reasoning -> reasoning.append(event.text)
        is ChatEvent.Sources -> sources += event.urls
        is ChatEvent.Failed -> failure = event.message
        ChatEvent.Done -> done = true
        is ChatEvent.Usage -> Unit
      }
    }

  return ProbeOutcome(
    done = done,
    text = text.toString().trim(),
    reasoning = reasoning.toString().trim(),
    failure = failure,
    responseModel = responseModel,
    sources = sources.toList(),
  )
}

private fun classifyVision(outcome: ProbeOutcome): CapabilityStatus {
  if (outcome.failure.isExplicitRejection()) return CapabilityStatus.Unsupported
  if (!outcome.done) return CapabilityStatus.Unknown
  val answer = "${outcome.text}\n${outcome.reasoning}".uppercase()
  return when {
    Regex("(^|[^A-Z])RED([^A-Z]|$)").containsMatchIn(answer) -> CapabilityStatus.Supported
    answer.isBlank() -> CapabilityStatus.Unknown
    else -> CapabilityStatus.Unsupported
  }
}

private fun classifyWebSearch(outcome: ProbeOutcome): CapabilityStatus {
  if (outcome.failure.isExplicitRejection()) return CapabilityStatus.Unsupported
  if (!outcome.done) return CapabilityStatus.Unknown
  val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
  return when {
    outcome.text.contains(today) -> CapabilityStatus.Supported
    outcome.text.isBlank() -> CapabilityStatus.Unknown
    else -> CapabilityStatus.Unsupported
  }
}

private fun outcomeDetail(outcome: ProbeOutcome, capability: String): String = when {
  outcome.failure.isNotBlank() -> outcome.failure
  outcome.done && outcome.text.isNotBlank() -> outcome.text.take(120)
  outcome.done -> "上游结束，但没有返回${capability}结果"
  else -> "没有返回结果"
}.take(180)

private fun String.isExplicitRejection(): Boolean =
  contains("HTTP 400") || contains("HTTP 404") || contains("HTTP 415") || contains("HTTP 422")

private fun identityStatus(requested: String, returned: String): ModelIdentityStatus {
  if (returned.isBlank()) return ModelIdentityStatus.Unknown
  val req = requested.trim().lowercase()
  val res = returned.trim().lowercase()
  val same = req == res || res.startsWith("$req-") || req.startsWith("$res-")
  return if (same) ModelIdentityStatus.Match else ModelIdentityStatus.Mismatch
}

/** 32×32 测试图：左半蓝、右半红。压缩后只有 103 字节。 */
private const val VisionTestPng =
  "data:image/png;base64," +
    "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAALklEQVR42u3NMQkAAAgAMJN4G8f+GMYMnsJg/yJ7TqbyJAQCgUAgEAgEAsGXYAHFGtg9wc7pDAAAAABJRU5ErkJggg=="
