package ai.lotus.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

private const val MaxErrorBodyChars = 32_768
private val ErrorJson = Json { ignoreUnknownKeys = true }

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
        if (code in 200..299) {
          conn.inputStream.bufferedReader().use { it.readText() }
        } else {
          readErrorBody(conn)
        }

      if (code !in 200..299) {
        return@withContext Probe.Err(upstreamHttpError(code, conn.contentType, body))
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
  /** OpenAI 兼容请求里的多图列表；与 [imageDataUrl] 同时存在时会合并。 */
  val imageDataUrls: List<String> = emptyList(),
  /** assistant 发起的工具调用。只用于紧跟其后的 tool 结果回合。 */
  val toolCalls: List<ChatToolCall> = emptyList(),
  /** role=tool 时，对应哪一次 assistant tool_call。 */
  val toolCallId: String? = null,
)

/** OpenAI 兼容函数工具声明。 */
data class ChatToolSpec(
  val name: String,
  val description: String,
  val parameters: JsonObject,
)

/** 模型真正发回的一次工具调用。 */
data class ChatToolCall(
  val id: String,
  val name: String,
  val arguments: String,
)

/** 流式聊天里往外吐的东西。 */
sealed interface ChatEvent {
  /** 上游在响应里回显的实际模型名。用来核对中转站有没有换模型。 */
  data class ModelInfo(val model: String) : ChatEvent

  /** 增量文本（可能是半个字、半个词）。 */
  data class Delta(val text: String) : ChatEvent

  /**
   * **推理过程**的增量（模型的"思考"）。
   *
   * ⚠️ 不是所有上游都吐这个：
   *  - 吐的（DeepSeek / Qwen / 多数国内兼容网关）：`delta.reasoning_content`；
   *  - 另一些（OpenRouter 一类）：`delta.reasoning`；
   *  - 不吐的：这一轮只有正文 —— 那就**如实什么都不显示**，绝不拿正文假装思考过程。
   */
  data class Reasoning(val text: String) : ChatEvent

  /**
   * **服务端报的真实用量**（`usage.prompt_tokens` / `completion_tokens`）。
   *
   * 这是唯一"真实映射"的来源 —— 客户端自己按字数估的 token 对不上任何模型的分词器。
   * 要靠请求里的 `stream_options.include_usage` 才会在流末尾多一帧 usage；
   * 提供方不支持时这一帧不会来，那就只能退回估算值（UI 上必须标出来）。
   */
  data class Usage(val promptTokens: Int, val completionTokens: Int) : ChatEvent

  /**
   * **它真的读过哪些网页** —— 上游在流里回的 `annotations`（URL 引用）。
   *
   * ⚠️ 两种实现都有：有的把 `annotations` 挂在 `delta` 上、有的挂在整帧上，所以下面两个位置都认。
   *    一把 URL 都没回（比如刚实测的 mzhcloud + deepseek）：**就什么都不发**，
   *    聊天页那行"访问记录"整行不出现 —— 不许拿正文里的链接冒充。
   */
  data class Sources(val urls: List<String>) : ChatEvent

  /** 模型请求调用客户端工具。调用方逐项执行并回填，随后允许模型继续决策。 */
  data class ToolCalls(val calls: List<ChatToolCall>) : ChatEvent

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
  tools: List<ChatToolSpec> = emptyList(),
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
                val images =
                  buildList {
                    t.imageDataUrl?.takeIf { it.isNotBlank() }?.let(::add)
                    addAll(t.imageDataUrls.filter { it.isNotBlank() })
                  }.distinct()
                when {
                  t.toolCallId != null -> {
                    put("tool_call_id", JsonPrimitive(t.toolCallId))
                    put("content", JsonPrimitive(t.content))
                  }
                  t.toolCalls.isNotEmpty() -> {
                    if (t.content.isBlank()) put("content", JsonNull)
                    else put("content", JsonPrimitive(t.content))
                    put(
                      "tool_calls",
                      JsonArray(
                        t.toolCalls.map { call ->
                          buildJsonObject {
                            put("id", JsonPrimitive(call.id))
                            put("type", JsonPrimitive("function"))
                            put(
                              "function",
                              buildJsonObject {
                                put("name", JsonPrimitive(call.name))
                                put("arguments", JsonPrimitive(call.arguments))
                              },
                            )
                          }
                        }
                      ),
                    )
                  }
                  images.isEmpty() -> put("content", JsonPrimitive(t.content))
                  else -> {
                    // 多模态：一段文字 + 多张图。OpenAI 兼容接口按 image_url 的数量逐张接收。
                    put(
                      "content",
                      JsonArray(
                        buildList {
                          add(
                            buildJsonObject {
                              put("type", JsonPrimitive("text"))
                              put("text", JsonPrimitive(t.content))
                            }
                          )
                          images.forEach { image ->
                            add(
                              buildJsonObject {
                                put("type", JsonPrimitive("image_url"))
                                put(
                                  "image_url",
                                  buildJsonObject { put("url", JsonPrimitive(image)) },
                                )
                              }
                            )
                          }
                        }
                      ),
                    )
                  }
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
        // 原生搜索和客户端 function tools 可以同时声明；后者由 Lotus 执行并逐轮回填。
        val toolDeclarations =
          buildList {
            if (webSearch) {
              add(buildJsonObject { put("type", JsonPrimitive("web_search")) })
            }
            tools.forEach { tool ->
              add(
                buildJsonObject {
                  put("type", JsonPrimitive("function"))
                  put(
                    "function",
                    buildJsonObject {
                      put("name", JsonPrimitive(tool.name))
                      put("description", JsonPrimitive(tool.description))
                      put("parameters", tool.parameters)
                    },
                  )
                }
              )
            }
          }
        if (toolDeclarations.isNotEmpty()) {
          put("tools", JsonArray(toolDeclarations))
          if (tools.isNotEmpty()) put("tool_choice", JsonPrimitive("auto"))
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
      val err = readErrorBody(conn)
      emit(ChatEvent.Failed(upstreamHttpError(code, conn.contentType, err)))
      return@flow
    }

    var serverStreamError = ""
    var sawDone = false
    var lastFinishReason = ""
    val pendingToolCalls = linkedMapOf<Int, PendingToolCall>()
    conn.inputStream.bufferedReader().use { reader ->
      var line = reader.readLine()
      stream@ while (line != null) {
        if (line.startsWith("data:")) {
          val payload = line.removePrefix("data:").trim()
          if (payload == "[DONE]") {
            sawDone = true
            break
          }
          streamError(payload)?.let {
            serverStreamError = it
            break@stream
          }
          responseModel(payload)?.let { emit(ChatEvent.ModelInfo(it)) }
          parseUsage(payload)?.let { emit(it) }
          finishReason(payload)?.let { lastFinishReason = it }
          // 先推理、后正文：上游通常先吐一段 reasoning_content，正文才跟着来。
          val reason = deltaReasoning(payload)
          if (reason.isNotEmpty()) emit(ChatEvent.Reasoning(reason))
          val text = deltaText(payload)
          if (text.isNotEmpty()) emit(ChatEvent.Delta(text))
          val cites = deltaSources(payload)
          if (cites.isNotEmpty()) emit(ChatEvent.Sources(cites))
          collectToolCallDeltas(payload, pendingToolCalls)
        }
        line = reader.readLine()
      }
    }
    if (serverStreamError.isNotBlank()) {
      emit(ChatEvent.Failed("上游流错误：${friendlyErrorText(serverStreamError, 180)}"))
      return@flow
    }
    if (!sawDone && lastFinishReason.isBlank()) {
      emit(ChatEvent.Failed("上游连接提前结束，回答可能未完成"))
      return@flow
    }
    when (lastFinishReason) {
      "length" -> {
        emit(ChatEvent.Failed("回答达到输出上限，内容可能未完成"))
        return@flow
      }
      "content_filter" -> {
        emit(ChatEvent.Failed("上游内容过滤器中断了这次回答"))
        return@flow
      }
    }
    val toolCalls = pendingToolCalls.toSortedMap().values.mapNotNull { it.build() }
    if (toolCalls.isNotEmpty()) emit(ChatEvent.ToolCalls(toolCalls))
    emit(ChatEvent.Done)
  } catch (t: Throwable) {
    emit(ChatEvent.Failed(t.message ?: t::class.java.simpleName))
  } finally {
    conn?.disconnect()
  }
}.flowOn(Dispatchers.IO)

private data class PendingToolCall(
  var id: String = "",
  var name: String = "",
  val arguments: StringBuilder = StringBuilder(),
) {
  fun build(): ChatToolCall? {
    if (name.isBlank()) return null
    return ChatToolCall(
      id = id.ifBlank { "lotus_${name}_${arguments.length}" },
      name = name,
      arguments = arguments.toString().ifBlank { "{}" },
    )
  }
}

/** 非 2xx 的错误体只读到有界长度，避免故障网关把整页 HTML 灌进内存和聊天正文。 */
private fun readErrorBody(conn: HttpURLConnection): String =
  runCatching {
      conn.errorStream?.bufferedReader()?.use { reader ->
        val out = StringBuilder()
        val buffer = CharArray(4_096)
        while (out.length < MaxErrorBodyChars) {
          val count = reader.read(buffer, 0, minOf(buffer.size, MaxErrorBodyChars - out.length))
          if (count <= 0) break
          out.append(buffer, 0, count)
        }
        out.toString()
      }.orEmpty()
    }
    .getOrDefault("")

/**
 * 把上游 HTTP 错误压成用户能读的一行。
 *
 * 中转站/gateway 故障时经常回整页 HTML。聊天正文绝不能显示整页标签；这里只保留状态码、常见
 * 原因、HTML title 和 Cloudflare Ray ID（有就带，便于排查），其余内容全部丢弃。
 */
internal fun upstreamHttpError(code: Int, contentType: String?, body: String): String {
  val type = contentType.orEmpty().lowercase()
  val trimmed = body.trimStart()
  val looksHtml =
    "text/html" in type ||
      "application/xhtml" in type ||
      trimmed.startsWith("<!doctype", ignoreCase = true) ||
      trimmed.startsWith("<html", ignoreCase = true) ||
      "<title" in trimmed.take(2_000).lowercase()

  if (looksHtml) {
    val title =
      runCatching {
          Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(80)
            ?.takeIf { it.isNotBlank() && !it.equals("error", ignoreCase = true) }
        }
        .getOrNull()
    val ray =
      runCatching {
          Regex("Ray ID:\\s*(?:</?[^>]+>\\s*)*([0-9a-f]{8,})", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
        }
        .getOrNull()
    return buildString {
      append(httpStatusReason(code))
      title?.let { append("（").append(it).append("）") }
      ray?.let { append(" · Ray ID ").append(it) }
    }
  }

  val jsonMessage = jsonErrorMessage(body)
  return if (jsonMessage.isNullOrBlank()) {
    if (body.isBlank()) httpStatusReason(code) else "${httpStatusReason(code)}：${friendlyErrorText(body, 160)}"
  } else {
    "${httpStatusReason(code)}：${friendlyErrorText(jsonMessage, 160)}"
  }
}

private fun httpStatusReason(code: Int): String =
  when (code) {
    400 -> "上游拒绝了请求（HTTP 400）"
    401 -> "API Key 无效或未授权（HTTP 401）"
    402 -> "账户余额不足（HTTP 402）"
    403 -> "上游拒绝访问（HTTP 403）"
    404 -> "接口或模型不存在（HTTP 404）"
    408 -> "上游请求超时（HTTP 408）"
    409 -> "上游请求冲突（HTTP 409）"
    413 -> "请求内容过大（HTTP 413）"
    415 -> "上游不支持这种请求格式（HTTP 415）"
    422 -> "上游无法处理这次请求（HTTP 422）"
    429 -> "请求过于频繁或额度已用完（HTTP 429）"
    500 -> "上游服务内部错误（HTTP 500）"
    502 -> "上游网关 502，服务暂不可用"
    503 -> "上游服务暂不可用（HTTP 503）"
    504 -> "上游网关超时（HTTP 504）"
    else -> "上游请求失败（HTTP $code）"
  }

private fun jsonErrorMessage(body: String): String? =
  runCatching {
      val root = ErrorJson.parseToJsonElement(body).jsonObject
      val error = root["error"]
      when (error) {
        is JsonPrimitive -> error.contentOrNull
        is JsonObject ->
          (error["message"] as? JsonPrimitive)?.contentOrNull
            ?: (error["detail"] as? JsonPrimitive)?.contentOrNull
            ?: (error["code"] as? JsonPrimitive)?.contentOrNull
        else ->
          (root["message"] as? JsonPrimitive)?.contentOrNull
            ?: (root["detail"] as? JsonPrimitive)?.contentOrNull
      }?.takeIf { it.isNotBlank() }
    }
    .getOrNull()

/** 防止异常文本里混入 HTML；多行错误也只展示一行。 */
private fun friendlyErrorText(raw: String, maxChars: Int): String {
  val text = raw.trim()
  if (text.isBlank()) return "（无错误详情）"
  val withoutTags =
    if (text.startsWith("<") || "<html" in text.take(2_000).lowercase() || "<!doctype" in text.take(2_000).lowercase()) {
      Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
        .ifBlank { "HTML 错误响应" }
    } else {
      text
    }
  return withoutTags
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(maxChars)
    .ifBlank { "（无错误详情）" }
}

/**
 * 流式 function call 的参数会拆成多个 JSON 片段。按 tool_calls[index] 累积，
 * 不在这里执行，也不允许模型在一次回答里串出多轮循环。
 */
private fun collectToolCallDeltas(
  payload: String,
  pending: MutableMap<Int, PendingToolCall>,
) {
  runCatching {
    val root = SseJson.parseToJsonElement(payload).jsonObject
    val choices = root["choices"] as? JsonArray ?: return
    val first = choices.firstOrNull() as? JsonObject ?: return
    val delta = first["delta"] as? JsonObject ?: first["message"] as? JsonObject ?: return
    val calls = delta["tool_calls"] as? JsonArray ?: return
    calls.forEachIndexed { fallbackIndex, element ->
      val call = element as? JsonObject ?: return@forEachIndexed
      val index = (call["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: fallbackIndex
      val slot = pending.getOrPut(index) { PendingToolCall() }
      (call["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { slot.id = it }
      val fn = call["function"] as? JsonObject ?: return@forEachIndexed
      (fn["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { slot.name += it }
      (fn["arguments"] as? JsonPrimitive)?.contentOrNull?.let { slot.arguments.append(it) }
    }
  }
}

/** 从 SSE 帧顶层读取 `model`。没有就返回 null，不拿请求参数冒充响应回显。 */
private fun responseModel(payload: String): String? =
  runCatching {
      val root = SseJson.parseToJsonElement(payload).jsonObject
      (root["model"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    .getOrNull()

/** SSE 有时 HTTP 200，但 data 里放的是 error 对象。不能把这种响应当成成功。 */
private fun streamError(payload: String): String? =
  runCatching {
      val root = SseJson.parseToJsonElement(payload).jsonObject
      val error = root["error"] ?: return null
      when (error) {
        is JsonPrimitive -> error.contentOrNull
        is JsonObject ->
          (error["message"] as? JsonPrimitive)?.contentOrNull
            ?: (error["code"] as? JsonPrimitive)?.contentOrNull
            ?: error.toString()
        else -> error.toString()
      }?.takeIf { it.isNotBlank() }
    }
    .getOrNull()

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

/** 推理字段的常见叫法，按优先级试。不同网关还会把同一段放在数组 / 对象里。 */
private val ReasoningKeys =
  listOf("reasoning_content", "reasoning_text", "reasoning", "thinking", "reasoning_details")

/** 把几种常见推理载荷统一压成纯文字。 */
private fun reasoningText(element: JsonElement?): String =
  when (element) {
    is JsonPrimitive -> element.contentOrNull.orEmpty()
    is JsonArray -> element.joinToString("") { reasoningText(it) }
    is JsonObject -> {
      val direct =
        listOf("text", "content", "summary", "delta")
          .asSequence()
          .map { reasoningText(element[it]) }
          .firstOrNull { it.isNotEmpty() }
          .orEmpty()
      if (direct.isNotEmpty()) direct
      else {
        // 有的实现用 `{type: "reasoning", details: [...]}` 再包一层。
        listOf("details", "parts", "items")
          .asSequence()
          .map { reasoningText(element[it]) }
          .firstOrNull { it.isNotEmpty() }
          .orEmpty()
      }
    }
    else -> ""
  }

/**
 * 从一条 SSE 分片里抠出**推理增量**。
 *
 * 各家命名不统一，所以常见的五个都认；一个都没有就返回空串 —— 调用方据此什么都不发。
 */
private fun deltaReasoning(payload: String): String =
  runCatching {
      val root = SseJson.parseToJsonElement(payload).jsonObject
      val first = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
      for (container in listOfNotNull(first?.get("delta") as? JsonObject, first?.get("message") as? JsonObject, root)) {
        for (k in ReasoningKeys) {
          val v = reasoningText(container[k])
          if (v.isNotEmpty()) return v
        }
      }
      ""
    }
    .getOrDefault("")

/** 读取 `choices[0].finish_reason`；流结束帧通常只带这个字段。 */
private fun finishReason(payload: String): String? =
  runCatching {
      val root = SseJson.parseToJsonElement(payload).jsonObject
      val first = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
      (first?.get("finish_reason") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    .getOrNull()

/**
 * 从一帧 SSE 里抠 `annotations` 里的 URL 引用。
 *
 * 兼容三种挂法（实测各家不统一，多认几种不吃亏）：
 *  - `choices[0].delta.annotations[].url_citation.url`（OpenAI 流式）
 *  - `choices[0].message.annotations[].url_citation.url`（非流式 / 有的网关流式也这么发）
 *  - 顶层 `citations[]` / `search_results[].url`（部分网关自己起的名）
 *
 * 一个都没解析到就返回空列表 —— 调用方据此什么都不发。
 */
private fun deltaSources(payload: String): List<String> =
  runCatching {
      val root = SseJson.parseToJsonElement(payload).jsonObject
      val out = LinkedHashSet<String>()

      fun eatAnnotations(el: JsonElement?) {
        val arr = el as? JsonArray ?: return
        arr.forEach { item ->
          val obj = item as? JsonObject ?: return
          // OpenAI：{type:"url_citation", url_citation:{url,title}}
          val cite = obj["url_citation"] as? JsonObject
          if (cite != null) {
            urlOf(cite["url"])?.let { out.add(it) }
            return@forEach
          }
          // 有的直接摊平：{url, title}
          urlOf(obj["url"])?.let { out.add(it) }
        }
      }

      (root["choices"] as? JsonArray)?.forEach { ch ->
        val c = ch as? JsonObject ?: return@forEach
        val d = c["delta"] as? JsonObject
        eatAnnotations(d?.get("annotations"))
        val m = c["message"] as? JsonObject
        eatAnnotations(m?.get("annotations"))
      }
      eatAnnotations(root["annotations"])
      (root["citations"] as? JsonArray)?.forEach { item ->
        when (item) {
          is JsonPrimitive -> urlOf(item)?.let { out.add(it) }
          is JsonObject -> urlOf(item["url"])?.let { out.add(it) }
          else -> Unit
        }
      }
      (root["search_results"] as? JsonArray)?.forEach { item ->
        val o = item as? JsonObject ?: return@forEach
        urlOf(o["url"])?.let { out.add(it) }
      }
      out.toList()
    }
    .getOrDefault(emptyList())

private fun urlOf(el: JsonElement?): String? =
  (el as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.startsWith("http") }
