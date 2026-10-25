package ai.lotus.app.shizuku

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * 运行在 Shizuku server 拉起的独立 UserService 进程中。
 *
 * 受限模式只实现明确列出的工具；用户开启“完全访问”并逐次批准后，才接收任意 shell。
 * 权限身份来自 Shizuku：
 * ADB 启动时为 uid=2000(shell)，root 启动时为 uid=0(root)。
 */
class LotusShizukuService : ILotusShizukuService.Stub {
  private val json = Json { ignoreUnknownKeys = true }

  constructor() : super()

  @Suppress("UNUSED_PARAMETER")
  constructor(context: Context) : super() {
    appContext = context.applicationContext ?: context
  }

  override fun destroy() {
    System.exit(0)
  }

  override fun status(): String =
    result(
      buildJsonObject {
        put("backend", "shizuku-user-service")
        put("uid", Process.myUid())
        put("pid", Process.myPid())
        put("identity", identityName(Process.myUid()))
        put("sdk", Build.VERSION.SDK_INT)
        put("android", Build.VERSION.RELEASE.orEmpty())
        put("model", Build.MODEL.orEmpty())
        put("manufacturer", Build.MANUFACTURER.orEmpty())
        put("selinux", runCatching { File("/proc/self/attr/current").readText().trim() }.getOrDefault(""))
      }
    )

  override fun execute(requestJson: String): String =
    try {
      val request = json.parseToJsonElement(requestJson).jsonObject
      val tool = request["tool"]?.jsonPrimitive?.contentOrNull.orEmpty()
      val args = request["args"] as? JsonObject ?: JsonObject(emptyMap())
      val data =
        when (tool) {
          "device.status" -> deviceStatus()
          "device.diagnostics" -> deviceDiagnostics(args)
          "device.shell_exec" -> shellExec(args)
          "device.apps" -> deviceApps(args)
          "device.foreground" -> foregroundApp()
          "device.setting" -> readSetting(args)
          "device.set_app_enabled" -> setAppEnabled(args)
          else -> return failure("不支持的系统工具：$tool")
        }
      result(data)
    } catch (t: Throwable) {
      failure(t.message ?: t::class.java.simpleName)
    }

  private fun deviceStatus(): JsonObject =
    buildJsonObject {
      put("uid", Process.myUid())
      put("pid", Process.myPid())
      put("identity", identityName(Process.myUid()))
      put("sdk", Build.VERSION.SDK_INT)
      put("android", Build.VERSION.RELEASE.orEmpty())
      put("model", Build.MODEL.orEmpty())
      put("manufacturer", Build.MANUFACTURER.orEmpty())
      put("device", Build.DEVICE.orEmpty())
      put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
      put("selinux", runCatching { File("/proc/self/attr/current").readText().trim() }.getOrDefault(""))
      put("cwd", File(".").absolutePath)
    }

  private fun deviceDiagnostics(args: JsonObject): JsonObject {
    val context = appContext ?: error("UserService 没有拿到 Context")
    val category = args.string("category").trim().lowercase()
    val limit = args.int("limit", 20).coerceIn(1, 100)
    val data =
      when (category) {
        "memory" -> DeviceDiagnostics.memory(context)
        "cpu" -> DeviceDiagnostics.cpu()
        "storage" -> DeviceDiagnostics.storage(context)
        "battery" -> DeviceDiagnostics.battery(context)
        "network" -> DeviceDiagnostics.network(context)
        "processes" ->
          DeviceDiagnostics.processes(
            runCommand("PATH=/system/bin:/system/xbin; ps -A -o PID,PPID,USER,RSS,NAME"),
            limit,
          )
        "thermal" ->
          DeviceDiagnostics.thermal(
            runCommand("PATH=/system/bin:/system/xbin; dumpsys thermalservice")
          )
        else -> error("不支持的系统诊断类别：$category")
      }
    return buildJsonObject {
      put("category", category)
      data.forEach { (key, value) -> put(key, value) }
    }
  }

  private fun shellExec(args: JsonObject): JsonObject {
    val command = args.string("command").trim()
    require(command.isNotBlank()) { "命令不能为空" }
    require(command.length <= 32_768) { "命令超过 32768 字符" }
    val timeoutMs =
      (args["timeoutMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.coerceIn(1_000L, 120_000L)
        ?: 30_000L
    val output = runCommand(command, timeoutMs)
    return buildJsonObject {
      put("command", command)
      put("stdout", output.trimEnd())
      put("outputChars", output.length)
      put("exitCode", 0)
      put("timeoutMs", timeoutMs)
      put("executorUid", Process.myUid())
      put("executor", identityName(Process.myUid()))
    }
  }

  private fun deviceApps(args: JsonObject): JsonObject {
    val context = appContext ?: error("UserService 没有拿到 Context")
    val query = args.string("query").trim().lowercase()
    val limit = args.int("limit", 80).coerceIn(1, 200)
    val pm = context.packageManager
    val apps =
      pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .asSequence()
        .filter { query.isBlank() || it.packageName.lowercase().contains(query) }
        .sortedBy { it.packageName }
        .take(limit)
        .map { info ->
          buildJsonObject {
            put("package", info.packageName)
            put("label", runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName))
            put("system", (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
          }
        }
        .toList()
    return buildJsonObject {
      put("count", apps.size)
      put("truncated", apps.size >= limit)
      put("apps", buildJsonArray { apps.forEach(::add) })
    }
  }

  private fun foregroundApp(): JsonObject {
    val context = appContext ?: error("UserService 没有拿到 Context")
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val task = am.getRunningTasks(1).firstOrNull()
    return buildJsonObject {
      put("package", task?.topActivity?.packageName.orEmpty())
      put("activity", task?.topActivity?.className.orEmpty())
      put("taskId", task?.id ?: -1)
    }
  }

  private fun readSetting(args: JsonObject): JsonObject {
    val context = appContext ?: error("UserService 没有拿到 Context")
    val namespace = args.string("namespace").ifBlank { "global" }.lowercase()
    val key = args.string("key")
    require(key.isNotBlank()) { "缺少设置项 key" }
    val resolver = context.contentResolver
    val value =
      when (namespace) {
        "system" -> Settings.System.getString(resolver, key)
        "secure" -> Settings.Secure.getString(resolver, key)
        else -> Settings.Global.getString(resolver, key)
      }
    return buildJsonObject {
      put("namespace", namespace)
      put("key", key)
      put("value", value)
    }
  }

  private fun setAppEnabled(args: JsonObject): JsonObject {
    val context = appContext ?: error("UserService 没有拿到 Context")
    val packageName = args.string("package").trim()
    require(packageName.isNotBlank()) { "缺少 package" }
    require(SystemToolSafety.packageNamePattern.matches(packageName)) { "非法包名：$packageName" }
    require(packageName !in SystemToolSafety.criticalPackages) { "拒绝操作系统关键应用：$packageName" }
    val enabled = args.boolean("enabled", true)
    val pm = context.packageManager
    val info = pm.getApplicationInfo(packageName, 0)
    require((info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
      "只允许操作系统应用：$packageName"
    }
    val before = pm.getApplicationEnabledSetting(packageName)
    val target =
      if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    pm.setApplicationEnabledSetting(packageName, target, 0)
    val after = pm.getApplicationEnabledSetting(packageName)
    return buildJsonObject {
      put("package", packageName)
      put("label", runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(packageName))
      put("enabled", enabled)
      put("before", before)
      put("after", after)
      put("verified", after == target)
      put("executorUid", Process.myUid())
      put("executor", identityName(Process.myUid()))
    }
  }

  private fun runCommand(command: String, timeoutMs: Long = 15_000L): String {
    val process =
      ProcessBuilder("/system/bin/sh", "-c", command)
        .redirectErrorStream(true)
        .start()
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
          name = "lotus-diagnostics-reader"
          start()
        }
    val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    if (!finished) {
      process.destroyForcibly()
      reader.join(1_000)
      error("诊断命令超时")
    }
    reader.join(2_000)
    val exit = process.exitValue()
    if (exit != 0) error("诊断命令失败（exit=$exit）：${output.toString().trim().take(300)}")
    return output.toString()
  }

  private fun identityName(uid: Int): String =
    when (uid) {
      0 -> "root"
      2000 -> "shell（ADB/Shizuku）"
      else -> "uid $uid"
    }

  private fun result(data: JsonElement): String =
    buildJsonObject {
        put("ok", true)
        put("data", data)
        put("uid", Process.myUid())
        put("pid", Process.myPid())
      }
      .toString()

  private fun failure(message: String): String =
    buildJsonObject {
        put("ok", false)
        put("error", message)
        put("uid", Process.myUid())
        put("pid", Process.myPid())
      }
      .toString()

  private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

  private fun JsonObject.int(key: String, fallback: Int): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: fallback

  private fun JsonObject.boolean(key: String, fallback: Boolean): Boolean =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: fallback

  private companion object {
    @Volatile var appContext: Context? = null

  }
}
