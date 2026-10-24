package ai.lotus.app.shizuku

data class PrivilegedRun(
  val ok: Boolean,
  val payload: String,
  val elapsedMs: Long,
  val tool: String,
  val backend: String,
) {
  val summary: String
    get() = if (ok) "$tool · ${elapsedMs}ms" else "$tool 失败 · ${elapsedMs}ms"
}
