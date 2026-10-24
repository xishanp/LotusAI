package ai.lotus.app.data

/**
 * 客户端真实执行完一次函数工具后的统一结果。
 *
 * [payload] 是回填给模型的 JSON；[log] 是给人看的过程摘要。两者都不允许由模型自报。
 */
data class ToolRunResult(
  val ok: Boolean,
  val payload: String,
  val elapsedMs: Long,
  val log: String,
  val displayName: String,
  val backend: String,
)
