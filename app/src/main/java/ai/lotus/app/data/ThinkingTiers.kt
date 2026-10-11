package ai.lotus.app.data

import android.content.Context

/**
 * 思考档位 —— **按模型实测**，不再按族别猜。
 *
 * 背景（用户口径）：「他有多少个就开多少个行不行？这些思考强度是真的吗？真实可靠的吗？
 * 6 Luna 可开不了 max 和 ultra」。
 *
 * 事实是：档位名是**上游自己**的事，同一个 `reasoning_effort` 字段，
 * 有的模型收 `max`、有的只收到 `high`、有的连 `minimal` 都不认。
 * 所以这里不写死任何模型的档位数量 —— 而是拿真请求去问上游：
 * 每个候选档位发一次极短请求（一句"1+1"），受理的留下、被拒的丢掉，
 * 结果连同日期一起落盘。面板上显示的就是**这个模型在这条上游上真正能开的档位**。
 */
val CandidateEfforts: List<String> =
  listOf("minimal", "low", "medium", "high", "xhigh", "max", "ultra")

/** 一次探测的结果。 */
data class TierProbeResult(
  /** 上游受理的档位，按 [CandidateEfforts] 的顺序。空 = 一个都不收（那就不给档位）。 */
  val accepted: List<String>,
  /** 被拒的档位 + 上游给的原文，直接摊给用户看，不藏。 */
  val rejected: Map<String, String>,
)

/** 探测结果的落盘（按 提供方 + 模型 分别存）。 */
object ThinkingTierStore {
  private const val FileName = "app_prefs"

  private fun key(providerId: String, modelId: String) = "tiers|$providerId|$modelId"

  /** null = 这个模型还没测过（面板要显示"本地推断"而不是"实测"）。 */
  fun load(context: Context, providerId: String, modelId: String): List<String>? {
    if (providerId.isBlank() || modelId.isBlank()) return null
    val raw = context.getSharedPreferences(FileName, Context.MODE_PRIVATE).getString(key(providerId, modelId), null)
    return raw?.split(",")?.filter { it.isNotBlank() }
  }

  fun save(context: Context, providerId: String, modelId: String, accepted: List<String>) {
    if (providerId.isBlank() || modelId.isBlank()) return
    context
      .getSharedPreferences(FileName, Context.MODE_PRIVATE)
      .edit()
      .putString(key(providerId, modelId), accepted.joinToString(","))
      .apply()
  }
}
