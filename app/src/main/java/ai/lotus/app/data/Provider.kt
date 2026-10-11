package ai.lotus.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 一个模型提供方（OpenAI 兼容）。
 *
 * **App 不预置任何厂商**：空列表起步，全由用户自己加。用户原话："模型提供方让别人自己填就行了"。
 *
 * @param models 从 `GET {baseUrl}/models` 拉回来的模型 id。没测过就是空的。
 * @param lastError 最近一次连接测试的失败原因；null = 没测过或上次成功。
 * @param lastTestedAt 最近一次测试的时间戳（毫秒），0 = 从没测过。
 */
@Serializable
data class ProviderConfig(
  val id: String,
  val name: String = "",
  val baseUrl: String = "",
  val apiKey: String = "",
  val models: List<String> = emptyList(),
  val lastError: String? = null,
  val lastTestedAt: Long = 0L,
  /**
   * 这个提供方的上下文窗口（token）。用来算"还剩多少 K"。
   *
   * 客户端拿不到模型的真实窗口（OpenAI 兼容的 /models 不返回这个），所以**必须让用户自己填**。
   * 默认 [DefaultContextWindow]，在提供方编辑页可改。
   */
  val contextWindow: Int = DefaultContextWindow,
) {
  /** 三项都填齐了没有。 */
  val isComplete: Boolean
    get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

  /** 界面上给这一行显示的状态文字。 */
  val statusText: String
    get() =
      when {
        !isComplete -> "待填写"
        models.isNotEmpty() -> "${models.size} 个模型"
        lastError != null -> "连接失败"
        else -> "未测试"
      }
}

/**
 * 提供方落盘。
 *
 * ⚠️ 暂时用 `filesDir/providers.json`，**不上 DataStore**：只为一件事 —— 退出 App 再进来
 * 不用重填。key 明文存本机私有目录（`/data/data/ai.lotus.app/files/`，非 root 读不到）。
 * 等做「会话与数据」那一页时再一起换成加密存储。
 */
object ProviderStore {
  private const val FileName = "providers.json"

  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

  fun load(context: Context): List<ProviderConfig> =
    runCatching {
        val file = File(context.filesDir, FileName)
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<ProviderConfig>>(file.readText())
      }
      .getOrElse { emptyList() }

  fun save(context: Context, providers: List<ProviderConfig>) {
    runCatching {
      File(context.filesDir, FileName).writeText(json.encodeToString(providers))
    }
  }
}

/**
 * "当前用的是哪个提供方 / 哪个模型"。
 *
 * 之前这两个只在内存里：**退出 App 再进来就复位成"没选模型"**，输入条上的 ⇄ 又变回一枚光秃秃的
 * 图标，用户看到的就是"我明明选过"。所以它也落盘，用 SharedPreferences（两个短字符串，
 * 不值得单开一个 json 文件）。
 */
object SelectionStore {
  private const val FileName = "selection"
  private const val KeyProvider = "providerId"
  private const val KeyModel = "modelId"

  fun load(context: Context): Pair<String?, String> {
    val prefs = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
    val provider = prefs.getString(KeyProvider, null)
    val model = prefs.getString(KeyModel, "").orEmpty()
    return provider to model
  }

  fun save(context: Context, providerId: String?, modelId: String) {
    context
      .getSharedPreferences(FileName, Context.MODE_PRIVATE)
      .edit()
      .putString(KeyProvider, providerId)
      .putString(KeyModel, modelId)
      .apply()
  }
}
