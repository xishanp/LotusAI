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
  val error: String? = null,
  /** 工具产生的图片先留在内存里，由聊天层落盘并挂到当前回答；绝不把 Base64 塞回模型上下文。 */
  val image: ToolImageAttachment? = null,
)

data class ToolImageAttachment(
  val base64: String,
  val mimeType: String = "image/png",
  val width: Int? = null,
  val height: Int? = null,
  val label: String = "截图",
)
