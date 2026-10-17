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
        version = "2026.10.19",
        lines =
          listOf(
            "首次启动重做：欢迎页 → 连接主模型 → 与小莲完成一轮真实初始化对话 → 进入主页；初始化阶段可以跳过，Key 和配置只保存在本机。",
            "记忆链路重做：IDENTITY / SOUL / AGENTS / USER / MEMORY 每轮全量读取；自动记忆按主题原位覆盖，旧版“用户 · 自动维护”等尾巴会自动迁移，不再越堆越长。",
            "补上记忆兜底：模型漏写隐藏标记时，客户端仍能从明确指令里记录称呼、语言、爱好和回答偏好，避免“嘴上记住了、文件没写”。",
            "思考过程接真实上游字段（reasoning_content / reasoning / reasoning_text）：回答时展开，结束后收起，点小莲头像可复查。",
            "工具与来源可追溯：记录本轮真实读写记忆、整理提示词等动作；联网引用支持在应用内查看页面标题和打开来源，不再拿正文链接冒充访问记录。",
            "聊天消息补齐一键复制、来源入口和系统文字选取；长按正文仍可选词、复制、全选，并修复链接解析导致的闪退。",
            "默认提示词改成通用版：去掉写死在出厂模板里的个人机型与关注领域；输入框从单行变多行时不再丢焦点、自动收起键盘。",
            "上下文压缩真正省请求：摘要只覆盖已压缩消息，后续请求只带摘要和未处理的新消息；原文继续保留在会话里供回看。",
            "手动“记住这段”改为按主题原位合并到 USER / MEMORY，不再追加带日期的记忆快照。",
            "默认模型页新增真实能力检测：视觉用测试图核对，原生联网核对当天日期，同时显示中转站回显的模型名和一致性。",
            "会话与数据新增密码加密备份：导入导出模型、会话、MD、头像和图片；换机可用，错误密码不会破坏现有数据。",
            "聊天消息操作统一收进每条消息最底部的同一行，移除重复的长按动作条和单独的文字复制键。",
            "设置新增独立“版本日志”页，当前版本与历史版本分层保留；检查更新继续直连 GitHub Releases。",
            "“关于”页移除快速测试版落款；原生联网只在模型实测支持时启用，避免不支持的工具声明导致整轮请求失败。",
          ),
      ),
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
