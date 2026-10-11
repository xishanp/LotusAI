package ai.lotus.app.data

import ai.lotus.app.BuildConfig

/**
 * 版本日志 —— **一个版本一段**，跟 [BuildConfig.VERSION_NAME] 对齐。
 *
 * 用户口径：「是每个版本的日志，你单独拎出来做一个」。所以要的是**按版本分条**，
 * 而不是散在界面里的一句硬编码文案。
 *
 * 维护方式：发新版时在最上面加一段，版本号必须跟 `app/build.gradle.kts` 里的 versionName 一字不差。
 * 写的时候只写**真的做了的**：功能、修复、去掉了什么。别写愿景，也别跟别的产品比。
 */
object Changelog {

  data class Entry(val version: String, val lines: List<String>)

  /** 新的排在最上面。 */
  val entries: List<Entry> =
    listOf(
      Entry(
        version = "2026.10.11",
        lines =
          listOf(
            "发布应用。",
            "全局液态玻璃：低模糊 + 边缘折射 + 色散，纯白底上也能一眼看出是玻璃。",
            "MD 记忆体系：IDENTITY / SOUL / AGENTS / USER / MEMORY 五份提示词文件 + memory/ 档案柜。",
            "模型能自己改提示词：追加用 `write`，整理自己的记忆段用 `tidy`。",
            "生图接进对话：出图时先摆一块扫色散的玻璃占位，图到了再收清楚。",
            "输入条多行自增高，删空自动缩回。",
            "附件支持图片 / 视频 / 文件。",
          ),
      ),
    )

  /** 当前版本的条目；版本号对不上就退回最新那一段（改 versionName 忘了改这里时不至于空白）。 */
  fun current(): Entry =
    entries.firstOrNull { it.version == BuildConfig.VERSION_NAME } ?: entries.first()

  /** 当前版本的日志正文，给「检查更新」弹层用。 */
  fun currentText(): String =
    buildString {
      append("v").append(current().version).append('\n')
      current().lines.forEach { append("· ").append(it).append('\n') }
    }.trimEnd()

  /** 全部版本的日志（历史也留着）。 */
  fun allText(): String =
    entries.joinToString("\n\n") { e ->
      buildString {
        append("v").append(e.version).append('\n')
        e.lines.forEach { append("· ").append(it).append('\n') }
      }.trimEnd()
    }
}
