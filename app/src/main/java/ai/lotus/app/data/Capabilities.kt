package ai.lotus.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 一个「外围能力」提供方（生图 / 生视频 / 搜索 / OCR 解析）。
 *
 * 为什么单独一份数据：主对话走的是 OpenAI 兼容的 chat 接口（[ProviderConfig]），而这些能力
 * 大多数模型**自己不带** —— 得另配一个服务。用户口径：「很多模型都没有生图或者视频能力，
 * 可以再多添一个多模态能力，然后放密钥进去」「有一些连视觉都没有的模型，需要用到的解析器
 * 也要弄一下，至少备在那里备用」。
 *
 * ⚠️ key 明文存本机私有目录（`/data/data/ai.lotus.app/files/`，非 root 读不到），跟 providers
 * 一样。**不许写进代码、不许写进界面、不许进日志**。
 */
@Serializable
data class CapabilityEndpoint(
  /** 服务名，随便填（Tavily / OpenAI / 自定义）。只影响显示。 */
  val provider: String = "",
  val baseUrl: String = "",
  val apiKey: String = "",
  /** 用哪个模型 id；搜索/ OCR 那种没有模型的留空。 */
  val model: String = "",
) {
  /** baseUrl + key 都填了才算配好。 */
  val configured: Boolean
    get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

  val statusText: String
    get() =
      when {
        !configured -> "未配置"
        provider.isBlank() -> "已配置"
        else -> provider
      }
}

/**
 * 四类外围能力。**都放在「默认模型」页里**（用户口径）。
 *
 * @param search 给"自己不带联网"的模型配的搜索源（Tavily 这类）。带上游 web_search 的模型不用它。
 * @param image  生图。
 * @param video  生视频。
 * @param ocr    图片/文档解析（连视觉都没有的模型，先把图里的字抠出来再喂给它）。
 */
@Serializable
data class Capabilities(
  val search: CapabilityEndpoint = CapabilityEndpoint(),
  val image: CapabilityEndpoint = CapabilityEndpoint(),
  val video: CapabilityEndpoint = CapabilityEndpoint(),
  val ocr: CapabilityEndpoint = CapabilityEndpoint(),
) {
  /**
   * 这一类能力**真的能用了没有**。
   *
   * ⚠️ 跟 [CapabilityEndpoint.configured] 的区别：生图 / 生视频 / 解析器**必须填模型 id**
   * 才算可用 —— 没有模型名的接口调用只会 400。用户点过名：「这个是必须填的」。
   */
  fun ready(kind: CapabilityKind): Boolean {
    val ep = get(kind)
    return ep.baseUrl.isNotBlank() &&
      ep.apiKey.isNotBlank() &&
      (!kind.needsModel || ep.model.isNotBlank())
  }

  fun get(kind: CapabilityKind): CapabilityEndpoint =
    when (kind) {
      CapabilityKind.Search -> search
      CapabilityKind.Image -> image
      CapabilityKind.Video -> video
      CapabilityKind.Ocr -> ocr
    }

  fun set(kind: CapabilityKind, value: CapabilityEndpoint): Capabilities =
    when (kind) {
      CapabilityKind.Search -> copy(search = value)
      CapabilityKind.Image -> copy(image = value)
      CapabilityKind.Video -> copy(video = value)
      CapabilityKind.Ocr -> copy(ocr = value)
    }
}

/**
 * 四类能力的种类。顺序 = 「默认模型」页里的展示顺序。
 *
 * @param needsModel 这一项**要不要填模型 id**。
 *   生图 / 生视频 / 解析器都要（不同模型的接口不一样）；**搜索源不要** ——
 *   Tavily 这类是固定接口的搜索服务，用 `Authorization: Bearer <key>` 打
 *   `POST https://api.tavily.com/search`，请求体里只有 `query`，**没有模型名**。
 *   用户口径：「这是种 MCP 服务呀，还有没有模型名称的呀」—— 所以别再给它套模型输入框了。
 * @param defaultBaseUrl 常见服务的固定接口地址，用户不填也能直接用。
 */
enum class CapabilityKind(
  val title: String,
  val hint: String,
  val needsModel: Boolean,
  val defaultBaseUrl: String = "",
) {
  Image("生图", "接口地址 + key + 模型，三样都要填；填好就能在对话里出图", needsModel = true),
  Video("生视频", "文生视频 / 图生视频", needsModel = true),
  Search(
    "搜索源",
    "Tavily 这类：只要 key，接口地址固定，没有模型名",
    needsModel = false,
    defaultBaseUrl = "https://api.tavily.com",
  ),
  Ocr("解析器", "连视觉都没有的模型：先把图 / 文档里的字抠出来", needsModel = true),
}

object CapabilityStore {
  private const val FileName = "capabilities.json"

  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

  fun load(context: Context): Capabilities =
    runCatching {
        val file = File(context.filesDir, FileName)
        if (!file.exists()) Capabilities()
        else json.decodeFromString<Capabilities>(file.readText())
      }
      .getOrElse { Capabilities() }

  fun save(context: Context, caps: Capabilities) {
    runCatching { File(context.filesDir, FileName).writeText(json.encodeToString(caps)) }
  }
}
