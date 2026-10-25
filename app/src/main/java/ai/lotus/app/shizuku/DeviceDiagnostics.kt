package ai.lotus.app.shizuku

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.roundToInt

/**
 * Root / Shizuku 共用的本机诊断读取器。
 *
 * 这里只读取系统状态，不执行用户输入的任意命令。进程和温度需要 shell 时，由两个后端分别用
 * 自己已验证的执行身份调用固定命令，再交给这里的解析器。
 */
internal object DeviceDiagnostics {
  private const val BytesPerKb = 1024L

  fun memory(context: Context): JsonObject {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val proc = parseMemInfoKb(
      runCatching { File("/proc/meminfo").readText() }.getOrDefault("")
    )

    val totalKb = proc["MemTotal"] ?: (info.totalMem / BytesPerKb)
    val availableKb = proc["MemAvailable"] ?: (info.availMem / BytesPerKb)
    val freeKb = proc["MemFree"] ?: availableKb
    val usedKb = (totalKb - availableKb).coerceAtLeast(0L)
    val swapTotalKb = proc["SwapTotal"] ?: 0L
    val swapFreeKb = proc["SwapFree"] ?: 0L

    return buildJsonObject {
      put("totalBytes", totalKb * BytesPerKb)
      put("availableBytes", availableKb * BytesPerKb)
      put("freeBytes", freeKb * BytesPerKb)
      put("usedBytes", usedKb * BytesPerKb)
      put("usedPercent", percent(usedKb, totalKb))
      put("buffersBytes", (proc["Buffers"] ?: 0L) * BytesPerKb)
      put("cachedBytes", (proc["Cached"] ?: 0L) * BytesPerKb)
      put("swapTotalBytes", swapTotalKb * BytesPerKb)
      put("swapFreeBytes", swapFreeKb * BytesPerKb)
      put("swapUsedBytes", (swapTotalKb - swapFreeKb).coerceAtLeast(0L) * BytesPerKb)
      put("lowMemory", info.lowMemory)
      put("thresholdBytes", info.threshold)
      put("source", if (proc.isEmpty()) "ActivityManager" else "/proc/meminfo")
    }
  }

  fun cpu(): JsonObject {
    val load = parseLoadAverage(
      runCatching { File("/proc/loadavg").readText() }.getOrDefault("")
    )
    val uptimeSeconds =
      runCatching { File("/proc/uptime").readText().substringBefore(' ').toDoubleOrNull() }
        .getOrNull()
    val cpuTicks = parseCpuTicks(
      runCatching { File("/proc/stat").readText().lineSequence().firstOrNull().orEmpty() }
        .getOrDefault("")
    )
    return buildJsonObject {
      put("cores", Runtime.getRuntime().availableProcessors())
      put("load1", load.getOrNull(0))
      put("load5", load.getOrNull(1))
      put("load15", load.getOrNull(2))
      put("runnableProcesses", load.getOrNull(3))
      put("totalProcesses", load.getOrNull(4))
      put("uptimeSeconds", uptimeSeconds)
      cpuTicks?.let { ticks ->
        put("cpuTicks", ticks)
      }
    }
  }

  fun storage(context: Context): JsonObject {
    val volumes = buildJsonArray {
      listOf(
        "data" to Environment.getDataDirectory(),
        "external" to Environment.getExternalStorageDirectory(),
      )
        .distinctBy { it.second.absolutePath }
        .forEach { (label, directory) ->
          volume(directory.absolutePath, label)?.let(::add)
        }
    }
    return buildJsonObject {
      put("volumes", volumes)
      put("primaryPath", context.filesDir.parentFile?.absolutePath.orEmpty())
    }
  }

  fun battery(context: Context): JsonObject {
    val intent =
      context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
    val percent = if (level >= 0) level * 100 / scale else -1
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) ?: Int.MIN_VALUE

    return buildJsonObject {
      put("levelPercent", percent)
      put("status", batteryStatus(status))
      put("health", batteryHealth(health))
      put("plugged", stringArray(pluggedLabel(plugged)))
      put("temperatureC", temperature.takeUnless { it == Int.MIN_VALUE }?.div(10.0))
      put("voltageV", voltage.takeUnless { it == Int.MIN_VALUE }?.div(1_000.0))
      put("technology", intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty())
      put("currentNowMicroamps", manager.optionalIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
      put("chargeCounterMicroampHours", manager.optionalIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
      put("capacityPercent", manager.optionalIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
    }
  }

  fun network(context: Context): JsonObject {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork
    val capabilities = network?.let(manager::getNetworkCapabilities)
    val link = network?.let(manager::getLinkProperties)
    return buildJsonObject {
      put("connected", capabilities != null)
      put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
      put("metered", manager.isActiveNetworkMetered)
      put("transports", stringArray(transportLabels(capabilities)))
      put("interfaceName", link?.interfaceName.orEmpty())
      put("dnsServers", buildJsonArray { link?.dnsServers?.forEach { add(JsonPrimitive(it.hostAddress.orEmpty())) } })
      put(
        "addresses",
        buildJsonArray {
          link?.linkAddresses?.forEach { address ->
            add(
              buildJsonObject {
                put("address", address.address.hostAddress.orEmpty())
                put("prefixLength", address.prefixLength)
              }
            )
          }
        },
      )
      put("downstreamKbps", capabilities?.linkDownstreamBandwidthKbps ?: 0)
      put("upstreamKbps", capabilities?.linkUpstreamBandwidthKbps ?: 0)
    }
  }

  fun processes(output: String, limit: Int): JsonObject = parseProcessList(output, limit)

  fun thermal(output: String): JsonObject = parseThermal(output)

  internal fun parseMemInfoKb(text: String): Map<String, Long> =
    text.lineSequence().mapNotNull { line ->
      val match = Regex("^([^:]+):\\s*(\\d+)\\s*kB\\s*$").find(line.trim())
        ?: return@mapNotNull null
      match.groupValues[1] to (match.groupValues[2].toLongOrNull() ?: return@mapNotNull null)
    }.toMap()

  internal fun parseLoadAverage(text: String): List<Double> {
    val parts = text.trim().split(Regex("\\s+"))
    if (parts.isEmpty()) return emptyList()
    val load = parts.take(3).mapNotNull { it.toDoubleOrNull() }
    val processParts = parts.getOrNull(3)?.split('/').orEmpty()
    return load +
      listOfNotNull(
        processParts.getOrNull(0)?.toDoubleOrNull(),
        processParts.getOrNull(1)?.toDoubleOrNull(),
      )
  }

  internal fun parseCpuTicks(line: String): JsonObject? {
    val parts = line.trim().split(Regex("\\s+"))
    if (parts.firstOrNull() != "cpu" || parts.size < 5) return null
    val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
    if (values.isEmpty()) return null
    return buildJsonObject {
      put("user", values.getOrElse(0) { 0L })
      put("nice", values.getOrElse(1) { 0L })
      put("system", values.getOrElse(2) { 0L })
      put("idle", values.getOrElse(3) { 0L })
      put("iowait", values.getOrElse(4) { 0L })
      put("irq", values.getOrElse(5) { 0L })
      put("softirq", values.getOrElse(6) { 0L })
      put("steal", values.getOrElse(7) { 0L })
    }
  }

  internal fun parseProcessList(output: String, limit: Int): JsonObject {
    val normalizedLimit = limit.coerceIn(1, 100)
    val rows =
      output.lineSequence().mapNotNull { line ->
        val match = ProcessRow.find(line) ?: return@mapNotNull null
        buildJsonObject {
          put("pid", match.groupValues[1].toIntOrNull() ?: return@mapNotNull null)
          put("ppid", match.groupValues[2].toIntOrNull() ?: return@mapNotNull null)
          put("user", match.groupValues[3])
          put("rssKb", match.groupValues[4].toLongOrNull() ?: return@mapNotNull null)
          put("rssBytes", (match.groupValues[4].toLongOrNull() ?: 0L) * BytesPerKb)
          put("name", match.groupValues[5].trim())
        }
      }.toList()
    val sorted =
      rows.sortedByDescending {
        it["rssKb"].toString().trim('"').toLongOrNull() ?: 0L
      }
    return buildJsonObject {
      put("total", rows.size)
      put("returned", minOf(sorted.size, normalizedLimit))
      put("truncated", sorted.size > normalizedLimit)
      put("processes", JsonArray(sorted.take(normalizedLimit)))
      put("rssUnit", "RSS is resident set size in kilobytes")
    }
  }

  internal fun parseThermal(output: String): JsonObject {
    val status =
      Regex("(?i)Thermal\\s+Status:\\s*(-?\\d+)").find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val temperatures =
      TemperatureLine.findAll(output)
        .take(64)
        .map { match ->
          buildJsonObject {
            put("name", match.groupValues[3])
            put("type", match.groupValues[2].toIntOrNull() ?: -1)
            put("valueC", match.groupValues[1].toDoubleOrNull())
            put("status", match.groupValues[4].toIntOrNull() ?: -1)
          }
        }
        .toList()
    return buildJsonObject {
      put("thermalStatus", status)
      put("temperatures", JsonArray(temperatures))
      put("temperatureCount", temperatures.size)
    }
  }

  private fun stringArray(values: List<String>): JsonArray =
    buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

  private fun volume(path: String, label: String): JsonObject? =
    runCatching {
      val stats = StatFs(path)
      val total = stats.blockCountLong * stats.blockSizeLong
      val available = stats.availableBlocksLong * stats.blockSizeLong
      val used = (total - available).coerceAtLeast(0L)
      buildJsonObject {
        put("label", label)
        put("path", path)
        put("totalBytes", total)
        put("availableBytes", available)
        put("usedBytes", used)
        put("usedPercent", if (total > 0) (used * 100.0 / total).roundToInt() else 0)
      }
    }.getOrNull()

  private fun percent(value: Long, total: Long): Int =
    if (total > 0) (value * 100.0 / total).roundToInt() else 0

  private fun BatteryManager.optionalIntProperty(property: Int): Int? =
    getIntProperty(property).takeUnless { it == Int.MIN_VALUE || it == 0 && property != BatteryManager.BATTERY_PROPERTY_CAPACITY }

  private fun batteryStatus(value: Int): String =
    when (value) {
      BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
      BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
      BatteryManager.BATTERY_STATUS_FULL -> "full"
      BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
      BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
      else -> "unavailable"
    }

  private fun batteryHealth(value: Int): String =
    when (value) {
      BatteryManager.BATTERY_HEALTH_GOOD -> "good"
      BatteryManager.BATTERY_HEALTH_COLD -> "cold"
      BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
      BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
      BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
      BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
      else -> "unknown"
    }

  private fun pluggedLabel(value: Int): List<String> =
    buildList {
      if (value and BatteryManager.BATTERY_PLUGGED_AC != 0) add("ac")
      if (value and BatteryManager.BATTERY_PLUGGED_USB != 0) add("usb")
      if (value and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) add("wireless")
      if (isEmpty()) add("none")
    }

  private fun transportLabels(capabilities: NetworkCapabilities?): List<String> =
    buildList {
      if (capabilities == null) return@buildList
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) add("wifi_aware")
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) add("lowpan")
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)) add("usb")
    }

  private val ProcessRow =
    Regex("""^\s*(\d+)\s+(\d+)\s+(\S+)\s+(\d+)\s+(.+?)\s*$""")

  private val TemperatureLine =
    Regex("""Temperature\{mValue=([-\d.]+),\s*mType=(\d+),\s*mName=([^,}]+),\s*mStatus=(\d+)}""")
}
