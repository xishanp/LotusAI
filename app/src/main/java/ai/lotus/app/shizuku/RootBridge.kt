package ai.lotus.app.shizuku

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

enum class RootPhase {
  NotRequested,
  Requesting,
  Ready,
  Denied,
  Error,
}

data class RootState(
  val phase: RootPhase = RootPhase.NotRequested,
  val message: String = "尚未获取 Root",
  val uid: Int = -1,
  val identity: String = "",
  val selinux: String = "",
) {
  val ready: Boolean get() = phase == RootPhase.Ready
}

/**
 * 应用自己的 Root 后端。
 *
 * 受限模式只开放下方固定工具；用户开启“完全访问”后，模型可请求任意 shell，
 * 但每条命令都会先在界面中展示，并暂停到用户逐次批准后才执行。
 * Android 没有一条名为“root”的普通运行时权限；实际授权由设备上的 root 管理器通过
 * `su` 弹窗完成。因此“获取 Root”第一次会触发系统的 su 授权，而不是应用权限弹窗。
 */
object RootBridge {
  private const val CommandTimeoutMs = 15_000L
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val json = Json { ignoreUnknownKeys = true }

  private val _state = MutableStateFlow(RootState())
  val state: StateFlow<RootState> = _state.asStateFlow()

  @Volatile private var appContext: Context? = null

  fun attach(context: Context) {
    appContext = context.applicationContext
  }

  /** 只能由用户点击按钮触发；冷启动不会主动弹 su 授权框。 */
  fun connectNow() {
    if (_state.value.phase == RootPhase.Requesting || _state.value.phase == RootPhase.Ready) return
    scope.launch {
      _state.value = RootState(RootPhase.Requesting, "正在向 root 管理器请求 su")
      val identity = runRoot("id")
      if (!identity.ok) {
        _state.value =
          RootState(
            phase = RootPhase.Denied,
            message = identity.error.ifBlank { "Root 未授权或设备没有 su" },
          )
        return@launch
      }
      val uid = Regex("\\buid=(\\d+)").find(identity.output)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
      if (uid != 0) {
        _state.value =
          RootState(
            phase = RootPhase.Denied,
            message = "su 返回的不是 root 身份（uid=$uid）",
            uid = uid,
            identity = identity.output.trim(),
          )
        return@launch
      }
      val selinux = runRoot("cat /proc/self/attr/current")
      _state.value =
        RootState(
          phase = RootPhase.Ready,
          message = "Root 已连接",
          uid = 0,
          identity = "root",
          selinux = selinux.output.trim().ifBlank { "未知" },
        )
    }
  }

  suspend fun execute(tool: String, args: JsonObject = JsonObject(emptyMap())): PrivilegedRun {
    val started = System.nanoTime()
    val state = _state.value
    if (!state.ready) {
      return failure(tool, started, "Root 尚未连接：${state.message}")
    }
    val data =
      runCatching {
          when (tool) {
            "device.status" -> deviceStatus(state)
            "device.diagnostics" -> deviceDiagnostics(args)
            "device.shell_exec" -> shellExec(args)
            "device.apps" -> deviceApps(args)
            "device.foreground" -> foregroundApp()
            "device.setting" -> readSetting(args)
            "device.set_app_enabled" -> setAppEnabled(args)
            else -> error("不支持的 Root 工具：$tool")
          }
        }
        .getOrElse { return failure(tool, started, it.message ?: it::class.java.simpleName) }

    val payload =
      buildJsonObject {
          put("ok", true)
          put("backend", "root-su")
          put("data", data)
          put("uid", 0)
          put("identity", "root")
        }
        .toString()
    return PrivilegedRun(
      ok = true,
      payload = payload,
      elapsedMs = elapsedSince(started),
      tool = tool,
      backend = "Root",
    )
  }

  private fun deviceStatus(state: RootState): JsonObject =
    buildJsonObject {
      put("uid", 0)
      put("identity", "root")
      put("sdk", Build.VERSION.SDK_INT)
      put("android", Build.VERSION.RELEASE.orEmpty())
      put("model", Build.MODEL.orEmpty())
      put("manufacturer", Build.MANUFACTURER.orEmpty())
      put("device", Build.DEVICE.orEmpty())
      put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
      put("selinux", state.selinux)
      put("executor", "su")
    }

  private suspend fun deviceDiagnostics(args: JsonObject): JsonObject {
    val context = appContext ?: error("RootBridge 没有拿到 Context")
    val category = args.string("category").trim().lowercase()
    val limit = args.int("limit", 20).coerceIn(1, 100)
    val data =
      when (category) {
        "memory" -> DeviceDiagnostics.memory(context)
        "cpu" -> DeviceDiagnostics.cpu()
        "storage" -> DeviceDiagnostics.storage(context)
        "battery" -> DeviceDiagnostics.battery(context)
        "network" -> DeviceDiagnostics.network(context)
        "processes" -> {
          val run = runRoot("PATH=/system/bin:/system/xbin; ps -A -o PID,PPID,USER,RSS,NAME")
          if (!run.ok) error(run.error)
          DeviceDiagnostics.processes(run.output, limit)
        }
        "thermal" -> {
          val run = runRoot("PATH=/system/bin:/system/xbin; dumpsys thermalservice")
          if (!run.ok) error(run.error)
          DeviceDiagnostics.thermal(run.output)
        }
        else -> error("不支持的系统诊断类别：$category")
      }
    return buildJsonObject {
      put("category", category)
      data.forEach { (key, value) -> put(key, value) }
    }
  }

  private suspend fun shellExec(args: JsonObject): JsonObject {
    val command = args.string("command").trim()
    require(command.isNotBlank()) { "命令不能为空" }
    require(command.length <= 32_768) { "命令超过 32768 字符" }
    val timeoutMs =
      (args["timeoutMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.coerceIn(1_000L, 120_000L)
        ?: 30_000L
    val run = runRoot(command, timeoutMs)
    if (!run.ok) error(run.error)
    return buildJsonObject {
      put("command", command)
      put("stdout", run.output.trimEnd())
      put("outputChars", run.output.length)
      put("exitCode", run.exitCode)
      put("timeoutMs", timeoutMs)
      put("executorUid", 0)
      put("executor", "root")
    }
  }

  private fun deviceApps(args: JsonObject): JsonObject {
    val context = appContext ?: error("RootBridge 没有拿到 Context")
    val query = args.string("query").trim().lowercase()
    val limit = args.int("limit", 40).coerceIn(1, 100)
    val pm = context.packageManager
    val all =
      pm.getInstalledApplications(0)
        .asSequence()
        .filter { query.isBlank() || it.packageName.lowercase().contains(query) }
        .sortedBy { it.packageName }
        .take(limit)
        .map { info ->
          buildJsonObject {
            put("package", info.packageName)
            put("label", runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName))
            put("system", (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
          }
        }
        .toList()
    return buildJsonObject {
      put("count", all.size)
      put("truncated", all.size >= limit)
      put("apps", buildJsonArray { all.forEach(::add) })
    }
  }

  private suspend fun foregroundApp(): JsonObject {
    val command = runRoot("PATH=/system/bin:/system/xbin; dumpsys activity activities")
    if (!command.ok) error(command.error)
    val match =
      Regex(
          """(?:mResumedActivity|topResumedActivity)[^\n]*?\s([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)"""
        )
        .find(command.output)
    return buildJsonObject {
      put("package", match?.groupValues?.getOrNull(1).orEmpty())
      put("activity", match?.groupValues?.getOrNull(2).orEmpty())
      put("executor", "root")
    }
  }

  private suspend fun readSetting(args: JsonObject): JsonObject {
    val namespace = args.string("namespace").ifBlank { "global" }.lowercase()
    require(namespace in setOf("system", "secure", "global")) { "非法设置命名空间：$namespace" }
    val key = args.string("key").trim()
    require(SystemToolSafety.settingKeyPattern.matches(key)) { "非法设置键：$key" }
    val run = runRoot("PATH=/system/bin:/system/xbin; settings get $namespace $key")
    if (!run.ok) error(run.error)
    val value = run.output.trim()
    return buildJsonObject {
      put("namespace", namespace)
      put("key", key)
      put("value", if (value == "null") null else value)
      put("executor", "root")
    }
  }

  private suspend fun setAppEnabled(args: JsonObject): JsonObject {
    val packageName = args.string("package").trim()
    SystemToolSafety.requireSafePackage(packageName)
    val enabled = args.boolean("enabled", true)

    val systemPackages =
      runRoot("PATH=/system/bin:/system/xbin; pm list packages -s")
    if (!systemPackages.ok) error(systemPackages.error)
    require(systemPackages.output.lineSequence().any { it.trim() == "package:$packageName" }) {
      "只允许操作系统应用：$packageName"
    }

    val before = runRoot("PATH=/system/bin:/system/xbin; pm list packages -e")
    val wasEnabled = before.output.lineSequence().any { it.trim() == "package:$packageName" }
    val action =
      if (enabled) {
        runRoot("PATH=/system/bin:/system/xbin; pm enable $packageName")
      } else {
        runRoot("PATH=/system/bin:/system/xbin; pm disable-user --user 0 $packageName")
      }
    if (!action.ok) error(action.error)

    val disabledNow = runRoot("PATH=/system/bin:/system/xbin; pm list packages -d")
    val enabledNow = runRoot("PATH=/system/bin:/system/xbin; pm list packages -e")
    val verified =
      if (enabled) {
        enabledNow.output.lineSequence().any { it.trim() == "package:$packageName" } &&
          disabledNow.output.lineSequence().none { it.trim() == "package:$packageName" }
      } else {
        disabledNow.output.lineSequence().any { it.trim() == "package:$packageName" }
      }
    val context = appContext
    val label =
      context
        ?.let { ctx ->
          runCatching {
              val pm = ctx.packageManager
              val info = pm.getApplicationInfo(packageName, 0)
              pm.getApplicationLabel(info).toString()
            }
            .getOrNull()
        }
        .orEmpty()
    return buildJsonObject {
      put("package", packageName)
      put("label", label.ifBlank { packageName })
      put("enabled", enabled)
      put("before", wasEnabled)
      put("after", enabled == verified)
      put("verified", verified)
      put("executorUid", 0)
      put("executor", "root")
      put("commandOutput", action.output.trim())
    }
  }

  private fun failure(tool: String, started: Long, message: String): PrivilegedRun {
    val payload =
      buildJsonObject {
          put("ok", false)
          put("backend", "root-su")
          put("error", message)
        }
        .toString()
    return PrivilegedRun(
      ok = false,
      payload = payload,
      elapsedMs = elapsedSince(started),
      tool = tool,
      backend = "Root",
    )
  }

  private data class RootCommand(val ok: Boolean, val output: String, val exitCode: Int, val error: String)

  private suspend fun runRoot(
    command: String,
    timeoutMs: Long = CommandTimeoutMs,
  ): RootCommand =
    withContext(Dispatchers.IO) {
      try {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        process.outputStream.close()
        val output = StringBuilder()
        val reader =
          Thread {
              runCatching {
                  process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                      if (output.length < 1_000_000) output.appendLine(line)
                    }
                  }
                }
            }
            .apply {
              isDaemon = true
              name = "lotus-root-reader"
              start()
            }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
          process.destroyForcibly()
          reader.join(1_000)
          return@withContext RootCommand(false, output.toString(), -1, "Root 命令超时")
        }
        reader.join(2_000)
        val exit = process.exitValue()
        RootCommand(
          ok = exit == 0,
          output = output.toString(),
          exitCode = exit,
          error = if (exit == 0) "" else "su 命令失败（exit=$exit）：${output.toString().trim().take(300)}",
        )
      } catch (t: Throwable) {
        RootCommand(false, "", -1, "无法执行 su：${t.message ?: t::class.java.simpleName}")
      }
    }

  private fun elapsedSince(started: Long): Long = (System.nanoTime() - started) / 1_000_000

  private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

  private fun JsonObject.int(key: String, fallback: Int): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: fallback

  private fun JsonObject.boolean(key: String, fallback: Boolean): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: fallback
}
