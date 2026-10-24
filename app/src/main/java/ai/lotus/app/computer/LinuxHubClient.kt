package ai.lotus.app.computer

import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownServiceException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal const val LinuxHubEndpoint = "http://127.0.0.1:38473"

internal data class LinuxHubHttpResponse(
  val code: Int,
  val body: String,
  val error: String? = null,
)

internal data class LinuxHubHealth(
  val ok: Boolean,
  val name: String,
  val version: String,
  val api: String,
  val initialized: Boolean,
  val containerUser: String?,
  val desktopRunning: Boolean,
  val serverError: String?,
)

data class LinuxHubTool(
  val apiName: String,
  val modelName: String,
  val description: String,
  val parameters: JsonObject,
)

/**
 * LinuxHub 本机 Agent 的唯一 HTTP 客户端。
 *
 * 只访问回环地址；Token 只放在 Authorization 头里，既不拼 URL，也不写日志。
 */
internal object LinuxHubClient {
  private const val MaxResponseBytes = 16 * 1024 * 1024
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  suspend fun health(token: String): LinuxHubHttpResponse =
    request(path = "/v1/health", token = token, readTimeoutMs = 5_000)

  suspend fun tools(token: String): LinuxHubHttpResponse =
    request(path = "/v1/tools", token = token, readTimeoutMs = 8_000)

  suspend fun callTool(
    token: String,
    tool: String,
    arguments: JsonObject,
    timeoutMs: Long,
  ): LinuxHubHttpResponse {
    val body =
      buildJsonObject {
        put("tool", tool)
        put("arguments", arguments)
      }.toString()
    return request(
      path = "/v1/tools/call",
      token = token,
      method = "POST",
      body = body,
      contentType = "application/json; charset=utf-8",
      readTimeoutMs = (timeoutMs + 5_000L).coerceIn(8_000L, 305_000L).toInt(),
    )
  }

  /** `/v1/cli/exec` 的请求体是纯文本命令，不是 JSON。 */
  suspend fun exec(token: String, command: String, timeoutMs: Long): LinuxHubHttpResponse =
    request(
      path = "/v1/cli/exec",
      token = token,
      method = "POST",
      body = command,
      contentType = "text/plain; charset=utf-8",
      extraHeaders = mapOf("X-Lotus-Timeout-Ms" to timeoutMs.toString()),
      readTimeoutMs = (timeoutMs + 5_000L).coerceIn(8_000L, 305_000L).toInt(),
    )

  fun parseHealth(body: String): LinuxHubHealth? =
    runCatching {
      val root = json.parseToJsonElement(body).jsonObject
      LinuxHubHealth(
        ok = root["ok"]?.jsonPrimitive?.booleanOrNull == true,
        name = root.string("name").ifBlank { "Lotus Local Agent" },
        version = root.string("version"),
        api = root.string("api"),
        initialized = root["initialized"]?.jsonPrimitive?.booleanOrNull == true,
        containerUser = root.string("containerUser").ifBlank { null },
        desktopRunning =
          runCatching {
            root["desktop"]?.jsonObject?.get("running")?.jsonPrimitive?.booleanOrNull == true
          }.getOrDefault(false),
        serverError = root.string("serverError").ifBlank { null },
      )
    }.getOrNull()

  fun parseTools(body: String): List<LinuxHubTool> =
    runCatching {
      val root = json.parseToJsonElement(body).jsonObject
      root["tools"]?.jsonArray.orEmpty().mapNotNull { element ->
        runCatching {
          val item = element.jsonObject
          val apiName = item.string("name")
          if (apiName.isBlank()) return@runCatching null
          LinuxHubTool(
            apiName = apiName,
            modelName = modelToolName(apiName),
            description = item.string("description"),
            parameters =
              runCatching { item["inputSchema"]?.jsonObject }.getOrNull()
                ?: emptyObjectSchema(),
          )
        }.getOrNull()
      }
    }.getOrDefault(emptyList())

  fun errorMessage(response: LinuxHubHttpResponse): String {
    response.error?.takeIf { it.isNotBlank() }?.let { return it }
    val parsed =
      runCatching {
        val root = json.parseToJsonElement(response.body).jsonObject
        root.string("error").ifBlank { null }
      }.getOrNull()
    if (!parsed.isNullOrBlank()) return parsed
    return when (response.code) {
      400 -> "LinuxHub 拒绝了这次调用"
      401, 403 -> "Token 无效或没有权限"
      404 -> "LinuxHub Agent 接口不存在"
      in 500..599 -> "LinuxHub Agent 内部错误"
      else -> "LinuxHub 返回 HTTP ${response.code}"
    }
  }

  fun modelToolName(apiName: String): String =
    "lotus_computer_" + apiName.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
      .joinToString("")

  private fun emptyObjectSchema(): JsonObject =
    buildJsonObject {
      put("type", "object")
      put("properties", buildJsonObject {})
      put("additionalProperties", false)
    }

  private fun JsonObject.string(key: String): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()

  private suspend fun request(
    path: String,
    token: String,
    method: String = "GET",
    body: String? = null,
    contentType: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
    readTimeoutMs: Int,
  ): LinuxHubHttpResponse =
    withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      try {
        connection =
          (URL(LinuxHubEndpoint + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 3_000
            readTimeout = readTimeoutMs
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            contentType?.let { setRequestProperty("Content-Type", it) }
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
          }

        val bytes = body?.toByteArray(StandardCharsets.UTF_8)
        if (bytes != null) {
          connection.doOutput = true
          connection.setFixedLengthStreamingMode(bytes.size)
          connection.outputStream.use { it.write(bytes) }
        }

        val code = connection.responseCode
        val stream =
          if (code in 200..299) connection.inputStream else connection.errorStream
        val limited = stream?.use { readLimited(it, MaxResponseBytes) }
        if (limited?.tooLarge == true) {
          LinuxHubHttpResponse(
            code = code,
            body = "",
            error = "LinuxHub 响应超过 16 MB 上限，已中止读取",
          )
        } else {
          LinuxHubHttpResponse(code = code, body = limited?.text.orEmpty())
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Throwable) {
        val friendly =
          when (failure) {
            is ConnectException -> "LinuxHub Agent 未启动，或 127.0.0.1:38473 没有监听"
            is SocketTimeoutException -> "LinuxHub Agent 响应超时"
            is UnknownServiceException -> "系统拒绝访问本机 HTTP 服务"
            else -> failure.message?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName
          }
        LinuxHubHttpResponse(code = 0, body = "", error = friendly)
      } finally {
        connection?.disconnect()
      }
    }

  private data class LimitedRead(val text: String, val tooLarge: Boolean)

  private fun readLimited(input: InputStream, maximum: Int): LimitedRead {
    val buffer = ByteArray(16 * 1024)
    val output = java.io.ByteArrayOutputStream()
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      val room = maximum + 1 - output.size()
      val accepted = minOf(count, room)
      if (accepted > 0) output.write(buffer, 0, accepted)
      if (output.size() > maximum) return LimitedRead(text = "", tooLarge = true)
    }
    return LimitedRead(
      text = output.toString(StandardCharsets.UTF_8.name()),
      tooLarge = false,
    )
  }
}
