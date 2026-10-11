package ai.lotus.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.ceil

/**
 * 一条落盘的消息。
 *
 * 和 UI 的 `ChatMessage` 是**同一个东西的两面**：UI 那份带 Compose 的稳定性，这份只管存取。
 * 转换只在 `LotusApp` 里做，数据层不引 Compose。
 */
@Serializable
data class StoredMessage(
  val id: Long,
  val fromUser: Boolean,
  val text: String,
  val at: Long = 0L,
  /**
   * 这条是哪个模型答的（只有助手消息有）。
   *
   * 为什么记在**消息上**而不是会话上：模型是可以中途换的，用户要能在聊天里一眼看出
   * "这句是谁答的"（对话设置里那个「显示模型」开关就是给它用的）。
   */
  val model: String = "",
  /** 这条答完时服务端报的 token 总量（prompt + completion）。0 = 没拿到（比如流断了）。 */
  val tokens: Int = 0,
  /**
   * 用户这条消息带的图（`filesDir/images/…` 的绝对路径）。空 = 没图。
   *
   * ⚠️ 只存**路径**，不存图片本身 —— 图片是文件，塞进 conversations.json 会把这个 json 撑爆。
   *    也不用 content:// （相册给的一次性授权重启就失效）。
   */
  val imagePath: String = "",
)

/**
 * 一个会话。
 *
 * @param title **用户改过的名字**。空 = 没改过，显示时用 [displayTitle] 从首条用户消息推。
 *   两态分开存是故意的：自动名会随首条消息变，用户改过的名字不许被覆盖。
 * @param updatedAt 最后一条消息的时间，历史列表按它倒序。
 */
@Serializable
data class Conversation(
  val id: String,
  val title: String = "",
  val createdAt: Long = 0L,
  val updatedAt: Long = 0L,
  val messages: List<StoredMessage> = emptyList(),
  /**
   * **压缩后的历史摘要**。非空时，发请求只带"摘要 + 最近 [keepRecent] 条"，
   * 比它更早的原文不进请求（但**界面里一直都在**，只是不再花钱）。
   */
  val summary: String = "",
  /**
   * 裁剪：一次请求最多带最近几条消息。0 = 不裁（全带）。
   * 跟 [summary] 配合：摘要管"记得住"，裁剪管"发得少"。
   */
  val keepRecent: Int = 0,
  /**
   * 最近一次请求**服务端报的真实 prompt token 数**（= 这次真正吃掉的上下文）。
   * 0 = 还没拿到真实值（没配提供方 / 提供方不回 usage），UI 那时必须标"估算"。
   */
  val promptTokens: Int = 0,
  /** 归档：不进历史列表的默认视图。内容一条不删，可以随时恢复。 */
  val archived: Boolean = false,
  /**
   * 这个会话里模型点名要过的记忆关键词（`<!--recall: …-->`，见 `Prompts.RecallProtocol`）。
   *
   * 存下来而不是每轮重算：关键词是模型自己的判断，重算一遍等于又让它做一次决策。
   * 发请求时按这些词去档案柜里取命中的段落（纯本地字符串匹配，零额外调用）。
   * 最多留最近 8 个 —— 攒成一大串就退化成"每轮全量读"了。
   */
  val recallKeys: List<String> = emptyList(),
  /**
   * **话题**：这一段在聊什么，一句话（模型生成）。
   *
   * ⚠️ 一个会话**只生成一次**（由 [topicTried] 把关）。它不是"每轮都总结" ——
   * 那正是 Codex 那种"聊一句花两份钱"的来源。
   */
  val topic: String = "",
  /** 已经为这个会话尝试过生成话题（失败也不重试，免得每轮都偷偷再发一次）。 */
  val topicTried: Boolean = false,
) {
  /** 列表里显示什么：用户改的名字 > 生成的话题 > 首条用户消息。 */
  val displayTitle: String
    get() = title.ifBlank { topic.ifBlank { autoTitle(messages) } }
}

/** 没起名字时，拿首条用户消息当名字。截 14 个字，够列表里认出是哪一次聊天。 */
fun autoTitle(messages: List<StoredMessage>): String {
  val first =
    messages.firstOrNull { it.fromUser && it.text.isNotBlank() }?.text
      ?: return "新会话"
  val line = first.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
  if (line.isEmpty()) return "新会话"
  return if (line.length <= 14) line else line.take(14) + "…"
}

/**
 * 会话落盘。
 *
 * 和 `ProviderStore` 一个路子：`filesDir/conversations.json`，退出再进不丢。
 * 明文存本机私有目录（`/data/data/ai.lotus.app/files/`，非 root 读不到）。
 */
object ConversationStore {
  private const val FileName = "conversations.json"

  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

  fun load(context: Context): List<Conversation> =
    runCatching {
        val file = File(context.filesDir, FileName)
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<Conversation>>(file.readText())
      }
      .getOrElse { emptyList() }

  fun save(context: Context, conversations: List<Conversation>) {
    runCatching {
      File(context.filesDir, FileName).writeText(json.encodeToString(conversations))
    }
  }
}

/** 没配提供方时用的上下文窗口默认值。配了就以提供方自己的 [ProviderConfig.contextWindow] 为准。 */
const val DefaultContextWindow: Int = 128_000

/**
 * token 估算。
 *
 * **这是估算，不是计费**。一个字对应多少 token 取决于模型的分词器，客户端拿不到，
 * 所以这里只求量级对得上：中日韩/全角按 0.8 记，其余按 1/4 记（英文约 4 字符 = 1 token），
 * 每条消息再加 4 的框架开销。
 *
 * 用途只有一个：让用户看见"这次聊天吃了多少上下文"。不参与任何请求参数。
 */
object Tokens {
  fun estimate(text: String): Int {
    if (text.isEmpty()) return 0
    var wide = 0
    var narrow = 0
    for (c in text) {
      if (isWide(c)) wide++ else narrow++
    }
    return ceil(wide * 0.8 + narrow / 4.0).toInt().coerceAtLeast(1)
  }

  /** 整段对话（含每条消息的框架开销）的估算值。 */
  fun estimate(messages: List<StoredMessage>): Int =
    messages.sumOf { estimate(it.text) + 4 } + 3

  private fun isWide(c: Char): Boolean {
    val code = c.code
    return code in 0x3000..0x303F || // CJK 标点
      code in 0x3040..0x30FF || // 假名
      code in 0x3400..0x4DBF || // 扩展 A
      code in 0x4E00..0x9FFF || // 基本汉字
      code in 0xAC00..0xD7AF || // 谚文
      code in 0xF900..0xFAFF || // 兼容汉字
      code in 0xFF00..0xFFEF // 全角
  }
}

/** 把 token 数写成一眼能读的短串：`820` / `12.4K` / `1.2M`。 */
fun formatTokens(tokens: Int): String =
  when {
    tokens < 1_000 -> tokens.toString()
    tokens < 1_000_000 -> "%.1fK".format(tokens / 1_000.0)
    else -> "%.1fM".format(tokens / 1_000_000.0)
  }
