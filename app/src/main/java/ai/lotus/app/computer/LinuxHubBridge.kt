package ai.lotus.app.computer

import ai.lotus.app.data.AppPrefs
import ai.lotus.app.data.ChatToolCall
import ai.lotus.app.data.ChatToolSpec
import ai.lotus.app.data.ToolImageAttachment
import ai.lotus.app.data.ToolRunResult
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

interface ComputerBackend {
  suspend fun status(context: Context): ComputerStatus

  suspend fun execute(
    context: Context,
    command: String,
    timeoutMs: Long = 60_000L,
  ): ComputerCommandResult
}

enum class ComputerPhase {
  Unavailable,
  Ready,
  Running,
  Error,
}

data class ComputerStatus(
  val phase: ComputerPhase = ComputerPhase.Unavailable,
  val message: String = "未连接 LinuxHub",
  val host: String = "LinuxHub",
  val capabilities: List<String> = emptyList(),
  val tools: List<LinuxHubTool> = emptyList(),
)

data class ComputerCommandResult(
  val ok: Boolean,
  val stdout: String = "",
  val stderr: String = "",
  val exitCode: Int? = null,
  val durationMs: Long = 0L,
) {
  fun displayText(): String =
    when {
      !ok && stderr.isNotBlank() -> stderr
      !ok -> "接口尚未连接"
      stdout.isNotBlank() -> stdout
      else -> "命令已完成，没有输出"
    }
}

data class ComputerToolCallResult(
  val ok: Boolean,
  val payload: String,
  val result: JsonObject?,
  val elapsedMs: Long,
  val error: String? = null,
)

/**
 * Lotus 与 LinuxHub 本机 Agent 的唯一适配层。
 *
 * 只访问 `127.0.0.1:38473`。服务是否可用由真实 `/v1/health` 决定；未验证时不会向模型
 * 暴露任何电脑工具。
 */
object LinuxHubBridge : ComputerBackend {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val verified = AtomicBoolean(false)
  private val stateRevision = AtomicLong(0)
  private val probeMutex = Mutex()

  @Volatile private var toolCache: List<LinuxHubTool> = emptyList()

  fun tokenConfigured(context: Context): Boolean = AppPrefs.linuxHubToken(context).isNotBlank()

  fun ready(): Boolean = verified.get()

  fun tools(): List<LinuxHubTool> = toolCache

  fun invalidate() {
    stateRevision.incrementAndGet()
    clearVerifiedState()
  }

  override suspend fun status(context: Context): ComputerStatus =
    probeMutex.withLock {
      val token = AppPrefs.linuxHubToken(context).trim()
      if (token.isBlank()) {
        invalidate()
        return@withLock ComputerStatus(
          phase = ComputerPhase.Unavailable,
          message = "还没有配置 Token。请从 LinuxHub → 设置 → Lotus的电脑 复制后粘贴到这里。",
          host = "LinuxHub · 未配置 Token",
        )
      }

      val revision = stateRevision.get()
      val healthResponse = LinuxHubClient.health(token)
      if (!stillCurrent(context, token, revision)) return@withLock staleStatus()
      if (healthResponse.code !in 200..299) {
        clearVerifiedState()
        val phase =
          if (healthResponse.code == 0) ComputerPhase.Unavailable else ComputerPhase.Error
        return@withLock ComputerStatus(
          phase = phase,
          message = LinuxHubClient.errorMessage(healthResponse),
          host = "LinuxHub · 未连接",
        )
      }
      val health = LinuxHubClient.parseHealth(healthResponse.body)
      if (health == null || !health.ok) {
        clearVerifiedState()
        return@withLock ComputerStatus(
          phase = ComputerPhase.Error,
          message = health?.serverError ?: "健康检查返回了无法识别的数据",
          host = "LinuxHub · 响应异常",
        )
      }

      val toolsResponse = LinuxHubClient.tools(token)
      if (!stillCurrent(context, token, revision)) return@withLock staleStatus()
      if (toolsResponse.code !in 200..299) {
        clearVerifiedState()
        return@withLock ComputerStatus(
          phase = ComputerPhase.Error,
          message = LinuxHubClient.errorMessage(toolsResponse),
          host = "LinuxHub v${health.version} · 工具目录读取失败",
        )
      }
      val tools = LinuxHubClient.parseTools(toolsResponse.body)
      if (tools.isEmpty()) {
        clearVerifiedState()
        return@withLock ComputerStatus(
          phase = ComputerPhase.Error,
          message = "LinuxHub 已连接，但没有返回可用工具。",
          host = "LinuxHub v${health.version} · 0 个工具",
        )
      }
      if (!stillCurrent(context, token, revision)) return@withLock staleStatus()

      toolCache = tools
      verified.set(true)
      val desktop = if (health.desktopRunning) "桌面运行中" else "桌面未启动"
      val user = health.containerUser?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
      ComputerStatus(
        phase = ComputerPhase.Ready,
        message =
          "已连接 ${health.name} v${health.version}，API ${health.api}。容器${
            if (health.initialized) "已初始化" else "尚未初始化"
          } · $desktop。",
        host = "LinuxHub v${health.version}$user · ${tools.size} 个工具",
        capabilities = capabilityLabels(tools),
        tools = tools,
      )
    }

  private fun stillCurrent(context: Context, token: String, revision: Long): Boolean {
    val current = AppPrefs.linuxHubToken(context).trim()
    val matches = revision == stateRevision.get() && current == token
    if (!matches) {
      stateRevision.incrementAndGet()
      clearVerifiedState()
    }
    return matches
  }

  private fun clearVerifiedState() {
    verified.set(false)
    toolCache = emptyList()
  }

  private fun staleStatus(): ComputerStatus =
    ComputerStatus(
      phase = ComputerPhase.Unavailable,
      message = "连接配置已变更，请重新检测。",
      host = "LinuxHub · 配置已变更",
    )

  override suspend fun execute(
    context: Context,
    command: String,
    timeoutMs: Long,
  ): ComputerCommandResult {
    if (command.isBlank()) {
      return ComputerCommandResult(ok = false, stderr = "命令不能为空")
    }
    val token = AppPrefs.linuxHubToken(context).trim()
    if (token.isBlank()) {
      return ComputerCommandResult(ok = false, stderr = "尚未配置 LinuxHub Token")
    }
    val response = LinuxHubClient.exec(token, command, timeoutMs)
    if (response.code !in 200..299) {
      return ComputerCommandResult(ok = false, stderr = LinuxHubClient.errorMessage(response))
    }
    val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
      ?: return ComputerCommandResult(ok = false, stderr = "LinuxHub 返回了无法识别的响应")
    val ok = root["ok"]?.jsonPrimitive?.booleanOrNull == true
    val result = runCatching { root["result"]?.jsonObject }.getOrNull()
    if (!ok || result == null) {
      return ComputerCommandResult(
        ok = false,
        stderr = root.string("error").ifBlank { "命令执行失败" },
      )
    }
    val exit = result["exitCode"]?.jsonPrimitive?.intOrNull
    val output = result.string("output")
    val timedOut = result["timedOut"]?.jsonPrimitive?.booleanOrNull == true
    val duration = result["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L
    return ComputerCommandResult(
      ok = exit == 0 && !timedOut,
      stdout = output,
      stderr =
        when {
          timedOut -> "命令执行超时"
          exit != null && exit != 0 -> "进程退出码 $exit"
          else -> ""
        },
      exitCode = exit,
      durationMs = duration,
    )
  }

  suspend fun callTool(
    context: Context,
    apiName: String,
    arguments: JsonObject,
    timeoutMs: Long = 60_000L,
  ): ComputerToolCallResult {
    if (apiName.isBlank()) {
      return ComputerToolCallResult(
        ok = false,
        payload = "{\"ok\":false,\"error\":\"电脑工具名为空\"}",
        result = null,
        elapsedMs = 0,
        error = "电脑工具名为空",
      )
    }
    val token = AppPrefs.linuxHubToken(context).trim()
    if (token.isBlank()) {
      return ComputerToolCallResult(
        ok = false,
        payload = "{\"ok\":false,\"error\":\"尚未配置 LinuxHub Token\"}",
        result = null,
        elapsedMs = 0,
        error = "尚未配置 LinuxHub Token",
      )
    }
    val started = System.currentTimeMillis()
    val response = LinuxHubClient.callTool(token, apiName, arguments, timeoutMs)
    val elapsed = System.currentTimeMillis() - started
    if (response.code !in 200..299) {
      val message = LinuxHubClient.errorMessage(response)
      return ComputerToolCallResult(
        ok = false,
        payload = buildJsonObject {
          put("ok", false)
          put("error", message)
        }.toString(),
        result = null,
        elapsedMs = elapsed,
        error = message,
      )
    }
    val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
    if (root == null) {
      return ComputerToolCallResult(
        ok = false,
        payload = "{\"ok\":false,\"error\":\"LinuxHub 返回了无法识别的响应\"}",
        result = null,
        elapsedMs = elapsed,
        error = "LinuxHub 返回了无法识别的响应",
      )
    }
    val reportedOk = root["ok"]?.jsonPrimitive?.booleanOrNull == true
    val resultElement = root["result"]
    val result = runCatching { resultElement?.jsonObject }.getOrNull()
    val ok = reportedOk && result != null
    return ComputerToolCallResult(
      ok = ok,
      payload = response.body,
      result = result,
      elapsedMs = elapsed,
      error =
        when {
          !reportedOk -> root.string("error").ifBlank { "工具执行失败" }
          resultElement == null || resultElement is JsonNull -> "LinuxHub 成功响应缺少 result"
          result == null -> "LinuxHub 成功响应的 result 不是对象"
          else -> null
        },
    )
  }

  private fun capabilityLabels(tools: List<LinuxHubTool>): List<String> =
    buildList {
      val names = tools.map { it.apiName }.toSet()
      if ("terminal.exec" in names) add("终端")
      if (names.any { it.startsWith("desktop.") && it != "desktop.screenshot" }) add("无头桌面")
      if ("desktop.screenshot" in names) add("截图")
      if (listOf("desktop.input", "desktop.key", "desktop.text").any { it in names }) add("鼠标键盘")
      if ("desktop.launch_app" in names) add("应用启动")
      if ("android.shizuku_status" in names) add("Shizuku")
      if ("system.info" in names) add("系统信息")
    }

  private fun JsonObject.string(key: String): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
}

/**
 * 模型侧的电脑工具注册表。
 *
 * LinuxHub 使用带点的工具名（terminal.exec）；OpenAI function name 不接受点，因此对模型
 * 暴露时只做名称映射。真正发给 LinuxHub 的仍是原始工具名。
 */
object ComputerToolContract {
  private const val ModelPrefix = "lotus_computer_"
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  val policy: String
    get() {
      val available =
        LinuxHubBridge.tools().joinToString("\n") {
          "- ${it.modelName} = ${it.apiName}：${it.description}"
        }
      return """
        电脑工具使用规则：
        - 只有用户明确要求操作 Lotus 的 Ubuntu 容器、无头桌面、图形应用或系统信息时，才调用下列工具。
        - 必须使用模型工具名 lotus_computer_*；不要直接输出 LinuxHub 的原始 API 名。
        - 不要在未调用工具时声称命令已经执行。
        - 无头桌面启动后要先用 lotus_computer_desktop_status 确认，再操作鼠标、键盘或截图。
        - 桌面截图和安卓截图会由客户端直接显示为聊天气泡里的图片；工具结果只包含摘要。
        - 用户要求查看桌面或手机画面时可以截图；不要声称自己“看到了”未由工具返回的内容。
        - 命令输出、退出码、耗时和错误必须来自工具结果，禁止编造。
        - 工具失败时说“未完成”并原样说明原因；成功时第一句用“已完成：”。
        - 可以连续调用多个工具：每次观察工具结果后再决定下一步。需要“启动桌面 → 查状态 → 截图”时就按顺序做完。
        - 互不依赖的读取可以放在同一批调用里；任务完成或失败原因已明确后停止调用并回答。
        当前可用工具：
        $available
      """.trimIndent()
    }

  fun specs(): List<ChatToolSpec> =
    LinuxHubBridge.tools().map { tool ->
      ChatToolSpec(
        name = tool.modelName,
        description = tool.description,
        parameters = tool.parameters,
      )
    }

  fun isComputerTool(modelName: String): Boolean = modelName.startsWith(ModelPrefix)

  suspend fun execute(context: Context, call: ChatToolCall): ToolRunResult {
    val tool = LinuxHubBridge.tools().firstOrNull { it.modelName == call.name }
      ?: return ToolRunResult(
        ok = false,
        payload = "{\"ok\":false,\"error\":\"电脑工具目录已变化，请重新检测连接\"}",
        elapsedMs = 0,
        log = "LinuxHub｜未知工具 ${call.name}",
        displayName = call.name,
        backend = "LinuxHub",
      )

    val arguments =
      runCatching { json.parseToJsonElement(call.arguments).jsonObject }
        .getOrElse { JsonObject(emptyMap()) }
    val timeout =
      runCatching {
        arguments["timeoutMs"]?.jsonPrimitive?.longOrNull
          ?: arguments["timeout_ms"]?.jsonPrimitive?.longOrNull
      }.getOrNull()?.coerceIn(1_000L, 300_000L) ?: 60_000L
    val run = LinuxHubBridge.callTool(context, tool.apiName, arguments, timeout)
    val image = if (run.ok) screenshotImage(tool.apiName, run) else null
    val payload = if (image != null) payloadWithoutImage(run) else run.payload
    val detail = toolDetail(tool.apiName, arguments, run, image != null)
    return ToolRunResult(
      ok = run.ok,
      payload = payload,
      elapsedMs = run.elapsedMs,
      log = "LinuxHub｜$detail · ${run.elapsedMs}ms" + if (run.ok) "" else " · 失败",
      displayName = call.name,
      backend = "LinuxHub",
      error = run.error,
      image = image,
    )
  }

  private fun toolDetail(
    apiName: String,
    arguments: JsonObject,
    run: ComputerToolCallResult,
    imageAttached: Boolean,
  ): String =
    when (apiName) {
      "terminal.exec" ->
        "终端 · " + arguments.string("command").lineSequence().firstOrNull().orEmpty().take(90)
      "desktop.start" ->
        "启动桌面 · ${arguments.string("width").ifBlank { "1280" }}×${arguments.string("height").ifBlank { "1024" }}"
      "desktop.stop" -> "停止桌面"
      "desktop.status" -> "桌面状态"
      "desktop.screenshot" -> {
        val bytes = run.result?.get("bytes")?.jsonPrimitive?.intOrNull
        "桌面截图" +
          (bytes?.let { " · ${it / 1024} KB" }.orEmpty()) +
          if (imageAttached) " · 已加入聊天" else ""
      }
      "desktop.input" -> "鼠标 · ${arguments.string("action").ifBlank { "input" }}"
      "desktop.key" -> "按键 · ${arguments.string("key")}"
      "desktop.text" -> "输入文字 · ${arguments.string("text").take(40)}"
      "desktop.launch_app" -> "启动应用 · ${arguments.string("desktopFile")}"
      "android.screenshot" -> "安卓截图" + if (imageAttached) " · 已加入聊天" else ""
      "android.shell" ->
        "Android 命令 · " + arguments.string("command").lineSequence().firstOrNull().orEmpty().take(70)
      "android.input" -> "Android 操作 · ${arguments.string("action").ifBlank { "input" }}"
      "android.install" -> "安装 APK · ${arguments.string("containerPath")}"
      "android.uninstall" -> "卸载应用 · ${arguments.string("packageName")}"
      "android.host_status" -> "Android 主机状态"
      "android.shizuku_status" -> "Shizuku 状态"
      "system.info" -> "系统信息"
      else -> apiName
    }

  private fun screenshotImage(apiName: String, run: ComputerToolCallResult): ToolImageAttachment? {
    if (apiName != "desktop.screenshot" && apiName != "android.screenshot") return null
    val result = run.result ?: return null
    val base64 = result.string("imageBase64").trim()
    if (base64.isBlank()) return null
    return ToolImageAttachment(
      base64 = base64,
      mimeType = result.string("mimeType").ifBlank { "image/png" },
      width = result["width"]?.jsonPrimitive?.intOrNull,
      height = result["height"]?.jsonPrimitive?.intOrNull,
      label = if (apiName == "desktop.screenshot") "桌面截图" else "安卓截图",
    )
  }

  /** Base64 只给 UI 落成文件；模型只拿不带大字段的 JSON，否则上下文会被一张图直接撑爆。 */
  private fun payloadWithoutImage(run: ComputerToolCallResult): String {
    val root = runCatching { json.parseToJsonElement(run.payload).jsonObject }.getOrNull()
      ?: return """{"ok":true,"imageAttachedToChat":true}"""
    val result = root["result"] as? JsonObject
    return buildJsonObject {
        root.forEach { (key, value) ->
          if (key != "imageBase64") put(key, value)
        }
        if (result != null) {
          put(
            "result",
            buildJsonObject {
              result.forEach { (key, value) ->
                if (key != "imageBase64") put(key, value)
              }
              put("imageAttachedToChat", true)
              put("note", "截图已直接显示在聊天界面；无需读取 Base64")
            },
          )
        } else {
          put("imageAttachedToChat", true)
        }
      }
      .toString()
  }

  private fun JsonObject.string(key: String): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
}
