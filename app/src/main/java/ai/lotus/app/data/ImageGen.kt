package ai.lotus.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 生图 —— 用户自己配的外围能力，**真的去调**（以前只存了配置，按了没反应）。
 *
 * 用户口径：「我已经配了生图模型，他直接调用不了」。
 *
 * 走的还是 OpenAI 兼容的 `/images/generations`：
 * ```
 * POST {baseUrl}/images/generations
 * { "model": "...", "prompt": "...", "n": 1, "size": "1024x1024" }
 * ```
 * 返回两种都认：`data[0].b64_json`（直接给图）和 `data[0].url`（给个链接，客户端自己下回来）。
 *
 * ⚠️ 这是**一次额外的 HTTP 调用**，但它不是 LLM 调用 —— 没有工具循环、不重复喂上下文，
 * 所以成本跟"聊一句"完全不是一个量级。触发条件也只有一个：模型在回复里写了
 * `<!--image: …-->`（见 [Prompts.ImageProtocol]），而那个协议只在用户配好生图服务时才注入。
 */
object ImageGen {
  private const val DirName = "images"

  private val json = Json { ignoreUnknownKeys = true }

  /** 调一次生图。成功 = [Result.path] 是落盘后的绝对路径；失败 = [Result.error] 是能给用户看的一句话。 */
  data class Result(val path: String? = null, val error: String? = null)

  suspend fun generate(context: Context, endpoint: CapabilityEndpoint, prompt: String): Result =
    withContext(Dispatchers.IO) {
      val base = endpoint.baseUrl.trim().trimEnd('/')
      if (base.isEmpty() || endpoint.apiKey.isBlank()) {
        return@withContext Result(error = "生图服务没配好（设置 → 默认模型 → 外围能力 → 生图）")
      }
      val body =
        buildJsonObject {
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
            if (endpoint.model.isNotBlank()) put("model", endpoint.model)
          }
          .toString()

      var conn: HttpURLConnection? = null
      try {
        conn =
          (URL("$base/images/generations").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${endpoint.apiKey.trim()}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
          }
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        val raw =
          (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (code !in 200..299) {
          return@withContext Result(error = "生图失败 HTTP $code：${raw.take(200).ifBlank { "（无内容）" }}")
        }

        val item =
          runCatching {
              val root = json.parseToJsonElement(raw) as JsonObject
              (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: (root["data"] as? JsonObject)
            }
            .getOrNull() ?: return@withContext Result(error = "生图返回里没有图片数据")

        val b64 = (item["b64_json"] as? JsonPrimitive)?.contentOrNull
        val url = (item["url"] as? JsonPrimitive)?.contentOrNull

        val bytes =
          when {
            !b64.isNullOrBlank() -> runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
            !url.isNullOrBlank() -> download(url)
            else -> null
          }
        if (bytes == null || bytes.isEmpty()) return@withContext Result(error = "生图返回的图片是空的")

        val file = File(File(context.filesDir, DirName).apply { mkdirs() }, "gen_${System.currentTimeMillis()}.png")
        file.writeBytes(bytes)
        Result(path = file.absolutePath)
      } catch (t: Throwable) {
        Result(error = "生图失败：${t.message ?: t::class.java.simpleName}")
      } finally {
        conn?.disconnect()
      }
    }

  /** `data[0].url` 那条路：把远端图下回本地（相册那种一次性链接不能用，必须自己存一份）。 */
  private fun download(url: String): ByteArray? =
    runCatching {
        var conn: HttpURLConnection? = null
        try {
          conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
              requestMethod = "GET"
              connectTimeout = 15_000
              readTimeout = 60_000
              setRequestProperty("User-Agent", "Mozilla/5.0 (Android) LotusAI")
            }
          if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
        } finally {
          conn?.disconnect()
        }
      }
      .getOrNull()
}
