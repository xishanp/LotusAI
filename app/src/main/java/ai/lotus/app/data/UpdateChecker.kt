package ai.lotus.app.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 「检查更新」。
 *
 * 用户口径：「检查更新，能做到吗？」—— 能。做法是查 **GitHub Releases**：
 * `GET https://api.github.com/repos/{repo}/releases/latest`，拿 `tag_name` 跟本机版本比。
 * 有新版就把 `html_url` 丢给浏览器去下载（**不在 App 里偷偷下 APK**，那要么需要未知来源权限、
 * 要么得自己做安装器，属于另一档事）。
 *
 * ⚠️ 更新源写死成 [Repo]。这个仓库还没建 / 还没发 Release 时，检查会如实返回「还没有 Release」，
 *    不假装成功。仓库一旦建好、打了 tag（比如 `v2026.10.18`），这里自动就能认出新版本。
 */
object UpdateChecker {
  /** 更新源仓库。改这里 = 改更新源。 */
  const val Repo = "xishanp/LotusAI"

  sealed interface Result {
    /** 有更新的版本。 */
    data class Newer(val version: String, val url: String) : Result

    /** 已经是最新（或本机比它还新）。 */
    data object UpToDate : Result

    /** 没查成功，[reason] 是要显示给用户的原因。 */
    data class Failed(val reason: String) : Result
  }

  /** 拉一次最新发布。**必须在 IO 线程调用**（阻塞式 HttpURLConnection）。 */
  fun check(currentRaw: String): Result {
    val current = currentRaw.substringBefore("-").trim()
    return runCatching {
        val conn =
          (URL("https://api.github.com/repos/$Repo/releases/latest").openConnection()
              as HttpURLConnection)
            .apply {
              requestMethod = "GET"
              connectTimeout = 12_000
              readTimeout = 12_000
              setRequestProperty("Accept", "application/vnd.github+json")
              setRequestProperty("User-Agent", "LotusAI-Android")
            }
        try {
          when (val code = conn.responseCode) {
            404 -> Result.Failed("读不到更新源：仓库可能是私有的，或还没有 Release")
            403 -> Result.Failed("GitHub 限流了，过一会儿再试")
            200 -> {
              val text = conn.inputStream.bufferedReader().use { it.readText() }
              val obj = Json.parseToJsonElement(text) as? JsonObject
              val tag = (obj?.get("tag_name") as? JsonPrimitive)?.contentOrNull.orEmpty()
              val url = (obj?.get("html_url") as? JsonPrimitive)?.contentOrNull.orEmpty()
              when {
                tag.isBlank() -> Result.Failed("读不到版本号")
                isNewer(tag, current) -> Result.Newer(tag.trimStart('v', 'V'), url)
                else -> Result.UpToDate
              }
            }
            else -> Result.Failed("HTTP $code")
          }
        } finally {
          conn.disconnect()
        }
      }
      .getOrElse { Result.Failed(it.message ?: "网络错误") }
  }

  /** 只比数字段：`v2026.10.11` vs `2026.10.9` → 后者旧。 */
  private fun isNewer(remote: String, current: String): Boolean {
    val a = remote.trimStart('v', 'V').split('.', '-', '_')
    val b = current.split('.', '-', '_')
    for (i in 0 until maxOf(a.size, b.size)) {
      val x = a.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
      val y = b.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
      if (x != y) return x > y
    }
    return false
  }
}
