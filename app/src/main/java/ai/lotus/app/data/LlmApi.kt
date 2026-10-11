package ai.lotus.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/** 一次连接测试的结果。 */
sealed interface Probe {
  data class Ok(val models: List<String>) : Probe

  data class Err(val message: String) : Probe
}

/**
 * 最小可用的 OpenAI 兼容客户端。
 *
 * 现在只有一件事：`GET {baseUrl}/models` —— 这就是「测试连接」。
 * 它同时验证三样东西：地址通不通、key 认不认、这个 key 底下到底有哪些模型。
 *
 * 刻意不引 OkHttp / Retrofit：`HttpURLConnection` + kotlinx.serialization 已经够，
 * 少两个依赖，APK 小一点、启动快一点。
 */
object LlmApi {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * 探一次 `/models`。
   *
   * @param baseUrl 形如 `https://host/v1`（尾部斜杠会被吃掉）。
   * @return [Probe.Ok] 带上模型 id 列表；[Probe.Err] 带一句**能直接给用户看**的原因。
   */
  suspend fun listModels(baseUrl: String, apiKey: String): Probe = withContext(Dispatchers.IO) {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) return@withContext Probe.Err("Base URL 是空的")
    if (!base.startsWith("http")) {
      return@withContext Probe.Err("Base URL 要以 http:// 或 https:// 开头")
    }

    var conn: HttpURLConnection? = null
    try {
      conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        setRequestProperty("Accept", "application/json")
      }
      val code = conn.responseCode
      val body =
        (if (code in 200..299) conn.inputStream else conn.errorStream)
          ?.bufferedReader()
          ?.use { it.readText() }
          .orEmpty()

      if (code !in 200..299) {
        return@withContext Probe.Err("HTTP $code：${body.take(220).ifBlank { "（无内容）" }}")
      }

      val ids = parseModelIds(body)
      if (ids.isEmpty()) {
        Probe.Err("通了，但 /models 里没有模型。多半是这把 key 没有聊天分组。")
      } else {
        Probe.Ok(ids)
      }
    } catch (t: Throwable) {
      Probe.Err(t.message ?: t::class.java.simpleName)
    } finally {
      conn?.disconnect()
    }
  }

  /** 兼容两种返回：`{"data":[{"id":"…"}]}`（OpenAI）和 `{"models":["…"]}`。 */
  private fun parseModelIds(body: String): List<String> {
    val root =
      runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
    (root["data"] as? JsonArray)
      ?.mapNotNull { (it as? JsonObject)?.get("id").asString() }
      ?.takeIf { it.isNotEmpty() }
      ?.let { return it.sorted() }
    (root["models"] as? JsonArray)?.mapNotNull { it.asString() }?.let { return it.sorted() }
    return emptyList()
  }

  private fun kotlinx.serialization.json.JsonElement?.asString(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/**
 * 一条对话历史。
 *
 * @param imageDataUrl 有图时走多模态：`content` 变成数组
 *   `[{type:text,…},{type:image_url,image_url:{url:data:image/jpeg;base64,…}}]`。
 *   见 [Attachments.dataUrl]。没图时还是纯字符串，别给所有请求都加数组。
 */
data class ChatTurn(
  val role: String,
  val content: String,
  val imageDataUrl: String? = null,
)

/** 流式聊天里往外吐的东西。 */
sealed interface ChatEvent {
  /** 增量文本（可能是半个字、半个词）。 */
  data class Delta(val text: String) : ChatEvent

  /**
   * **服务端报的真实用量**（`usage.prompt_tokens` / `completion_tokens`）。
   *
   * 这是唯一"真实映射"的来源 —— 客户端自己按字数估的 token 对不上任何模型的分词器。
   * 要靠请求里的 `stream_options.include_usage` 才会在流末尾多一帧 usage；
   * 提供方不支持时这一帧不会来，那就只能退回估算值（UI 上必须标出来）。
   */
  data class Usage(val promptTokens: Int, val completionTokens: Int) : ChatEvent

  /** 失败，带一句能直接给用户看的原因。 */
  data class Failed(val message: String) : ChatEvent

  /** 正常结束。 */
  data object Done : ChatEvent
}

/**
 * 流式聊天（OpenAI 兼容 `/chat/completions`，`stream: true`）。
 *
 * 用 **SSE 逐行读**，不引 OkHttp：`HttpURLConnection` 的 `inputStream` 本身就是流式的，
 * `bufferedReader().lineSequence()` 边到边吐，首字延迟和官方 SDK 没差别。
 *
 * @param reasoningEffort 传给 `reasoning_effort`；null = 不传（不支持的模型别硬塞）。
 */
fun LlmApi.chatStream(
  baseUrl: String,
  apiKey: String,
  model: String,
  turns: List<ChatTurn>,
  reasoningEffort: String? = null,
  maxTokens: Int? = null,
  /**
   * 联网搜索（服务端执行，客户端**不**多发任何请求）。
   *
   * 实测（mzhcloud / gpt-6-luna）：请求体里带 `tools:[{"type":"web_search"}]` 时，上游自己
   * 去搜、把结果塞进输入再回答 —— 客户端侧依然只有**一次**调用，符合"不做 Agent"的定位。
   * 代价：开了之后那次请求的 prompt_tokens 会从几十涨到几千（搜索结果本身很占地），
   * 所以默认关闭、由用户在设置里自己开。
   */
  webSearch: Boolean = false,
): Flow<ChatEvent> = flow {
  val base = baseUrl.trim().trimEnd('/')
  if (base.isEmpty() || !base.startsWith("http")) {
    emit(ChatEvent.Failed("Base URL 没填对"))
    return@flow
  }
  if (model.isBlank()) {
    emit(ChatEvent.Failed("没选模型"))
    return@flow
  }

  val body =
    buildJsonObject {
        put("model", JsonPrimitive(model))
        put("stream", JsonPrimitive(true))
        put(
          "messages",
          JsonArray(
            turns.map { t ->
              buildJsonObject {
                put("role", JsonPrimitive(t.role))
                val image = t.imageDataUrl
                if (image.isNullOrBlank()) {
                  put("content", JsonPrimitive(t.content))
                } else {
                  // 多模态：文字 + 图。OpenAI 兼容的格式，看图模型都吃这一套。
                  put(
                    "content",
                    JsonArray(
                      listOf(
                        buildJsonObject {
                          put("type", JsonPrimitive("text"))
                          put("text", JsonPrimitive(t.content))
                        },
                        buildJsonObject {
                          put("type", JsonPrimitive("image_url"))
                          put(
                            "image_url",
                            buildJsonObject { put("url", JsonPrimitive(image)) },
                          )
                        },
                      )
                    ),
                  )
                }
              }
            }
          ),
        )
        // 要真实 token 用量就必须带这一行；不带的话服务端不会在流末尾补 usage 帧。
        put(
          "stream_options",
          buildJsonObject { put("include_usage", JsonPrimitive(true)) },
        )
        if (reasoningEffort != null) put("reasoning_effort", JsonPrimitive(reasoningEffort))
        if (maxTokens != null) put("max_tokens", JsonPrimitive(maxTokens))
        // 联网搜索：只加一个声明，真正的搜索和抓取都在上游做。
        // 注意是 `{"type":"web_search"}`（原生搜索），不是自定义 function ——
        // 后者会让模型回一个 tool_call 交给客户端执行，那就变成 Agent 循环了，正是要避开的。
        if (webSearch) {
          put(
            "tools",
            JsonArray(listOf(buildJsonObject { put("type", JsonPrimitive("web_search")) })),
          )
        }
      }
      .toString()

  var conn: HttpURLConnection? = null
  try {
    conn =
      (URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 120_000
        doOutput = true
        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "text/event-stream")
      }
    conn.outputStream.use { it.write(body.toByteArray()) }

    val code = conn.responseCode
    if (code !in 200..299) {
      val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
      emit(ChatEvent.Failed("HTTP $code：${err.take(220).ifBlank { "（无内容）" }}"))
      return@flow
    }

    conn.inputStream.bufferedReader().use { reader ->
      var line = reader.readLine()
      while (line != null) {
        if (line.startsWith("data:")) {
          val payload = line.removePrefix("data:").trim()
          if (payload == "[DONE]") break
          parseUsage(payload)?.let { emit(it) }
          val text = deltaText(payload)
          if (text.isNotEmpty()) emit(ChatEvent.Delta(text))
        }
        line = reader.readLine()
      }
    }
    emit(ChatEvent.Done)
  } catch (t: Throwable) {
    emit(ChatEvent.Failed(t.message ?: t::class.java.simpleName))
  } finally {
    conn?.disconnect()
  }
}.flowOn(Dispatchers.IO)

/**
 * 从一帧 SSE 里抠 `usage`。没有 usage 就返回 null（绝大多数帧都没有，只有流末尾那一帧有）。
 *
 * OpenAI 的格式：`{"choices":[],"usage":{"prompt_tokens":123,"completion_tokens":45,...}}`
 * 也兼容 `input_tokens` / `output_tokens`（部分兼容实现用这一套命名）。
 */
private fun parseUsage(payload: String): ChatEvent.Usage? =
  runCatching {
      val usage = SseJson.parseToJsonElement(payload).jsonObject["usage"] as? JsonObject
        ?: return null
      val prompt =
        (usage["prompt_tokens"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
          ?: (usage["input_tokens"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
          ?: return null
      val completion =
        (usage["completion_tokens"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
          ?: (usage["output_tokens"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
          ?: 0
      ChatEvent.Usage(prompt, completion)
    }
    .getOrNull()

/** SSE 分片解析用的 Json（顶层，给 [deltaText] 用）。 */
private val SseJson = Json { ignoreUnknownKeys = true }

/** 从一条 SSE 分片里抠出 `choices[0].delta.content`。 */
private fun deltaText(payload: String): String =
  runCatching {
      val root = SseJson.parseToJsonElement(payload).jsonObject
      val choices = root["choices"] as? JsonArray ?: return ""
      val first = choices.firstOrNull() as? JsonObject ?: return ""
      val delta = first["delta"] as? JsonObject ?: return ""
      (delta["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
    .getOrDefault("")
