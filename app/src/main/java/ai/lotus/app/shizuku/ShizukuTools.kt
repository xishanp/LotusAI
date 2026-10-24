package ai.lotus.app.shizuku

import ai.lotus.app.data.ChatToolCall
import ai.lotus.app.data.ChatToolSpec
import ai.lotus.app.data.ToolRunResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 暴露给模型的 Shizuku 工具。名称只用 OpenAI function name 允许的字符。 */
object ShizukuTools {
  /**
   * 只在已授权且本轮声明了系统工具时注入。约束必须写在 system 层，
   * 不能靠界面文案或模型自己猜。
   */
  val policy: String =
    """
    系统工具使用规则：
    - 只有用户明确要求读取设备信息、应用列表、前台应用或系统设置时才调用读取工具。
    - 只有用户明确点名某个非关键系统应用并要求冻结或解冻时，才允许调用 system_set_app_enabled。
    - 每轮最多调用一个工具；工具结果返回后直接完成回答，不要再次请求工具。
    - 不得声称执行过没有实际执行的系统操作。工具失败时要如实说明。
    - 工具成功后，回答第一句固定用「已完成：」，直接说对象和新状态；禁止使用「确实、应该、可能」这类不确定措辞。
    - 工具失败后，回答第一句固定用「未完成：」，再说明失败原因。
    - 不提供任意 shell；系统关键应用永远禁止冻结。
    - Root 后端优先；Root 未连接时才使用 Shizuku。不要虚报当前后端。
    """.trimIndent()

  private val json = Json { ignoreUnknownKeys = true }
  private val emptySchema = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {})
    put("additionalProperties", false)
  }

  val specs: List<ChatToolSpec> =
    listOf(
      ChatToolSpec(
        name = "system_device_status",
        description =
          "读取本机真实的系统身份、Android 版本、机型与当前特权后端。只在用户询问设备、权限或排查工具连接时调用。",
        parameters = emptySchema,
      ),
      ChatToolSpec(
        name = "system_list_apps",
        description =
          "通过 Root 或 Shizuku 权限读取已安装应用列表。可按包名筛选；默认最多返回 40 个。",
        parameters =
          buildJsonObject {
            put("type", "object")
            put(
              "properties",
              buildJsonObject {
                put(
                  "query",
                  buildJsonObject {
                    put("type", "string")
                    put("description", "可选，包名或应用名片段；为空则列出应用")
                  },
                )
                put(
                  "limit",
                  buildJsonObject {
                    put("type", "integer")
                    put("description", "返回数量，1-100，默认 40")
                    put("minimum", 1)
                    put("maximum", 100)
                  },
                )
              },
            )
            put("additionalProperties", false)
          },
      ),
      ChatToolSpec(
        name = "system_set_app_enabled",
        description =
          "冻结（enabled=false）或解冻（enabled=true）一个非关键系统应用。必须由用户明确点名包名；系统关键应用会被客户端拒绝。",
        parameters =
          buildJsonObject {
            put("type", "object")
            put(
              "properties",
              buildJsonObject {
                put(
                  "package",
                  buildJsonObject {
                    put("type", "string")
                    put("description", "完整 Android 包名，例如 com.android.chrome")
                  },
                )
                put(
                  "enabled",
                  buildJsonObject {
                    put("type", "boolean")
                    put("description", "true=解冻并启用；false=冻结并禁用")
                  },
                )
              },
            )
            put("required", kotlinx.serialization.json.buildJsonArray {
              add(kotlinx.serialization.json.JsonPrimitive("package"))
              add(kotlinx.serialization.json.JsonPrimitive("enabled"))
            })
            put("additionalProperties", false)
          },
      ),
      ChatToolSpec(
        name = "system_foreground_app",
        description = "读取当前前台应用的包名和 Activity。只在用户问当前正在使用什么应用时调用。",
        parameters = emptySchema,
      ),
      ChatToolSpec(
        name = "system_read_setting",
        description =
          "读取一项 Android 系统设置。namespace 只能是 system、secure、global；key 必须是真实设置键。只读，不修改。",
        parameters =
          buildJsonObject {
            put("type", "object")
            put(
              "properties",
              buildJsonObject {
                put(
                  "namespace",
                  buildJsonObject {
                    put("type", "string")
                    put("enum", kotlinx.serialization.json.buildJsonArray {
                      add(kotlinx.serialization.json.JsonPrimitive("system"))
                      add(kotlinx.serialization.json.JsonPrimitive("secure"))
                      add(kotlinx.serialization.json.JsonPrimitive("global"))
                    })
                  },
                )
                put("key", buildJsonObject { put("type", "string") })
              },
            )
            put("required", kotlinx.serialization.json.buildJsonArray {
              add(kotlinx.serialization.json.JsonPrimitive("namespace"))
              add(kotlinx.serialization.json.JsonPrimitive("key"))
            })
            put("additionalProperties", false)
          },
      ),
    )

  /**
   * 这一轮是否有可用的特权后端。Root 是应用自己的 su 后端；Shizuku 是兼容后端。
   * 两者都不在时，模型请求里不会声明系统工具。
   */
  fun ready(): Boolean = RootBridge.state.value.ready || ShizukuBridge.state.value.ready

  suspend fun execute(call: ChatToolCall): ToolRunResult {
    val args = runCatching { json.parseToJsonElement(call.arguments).jsonObject }.getOrElse { JsonObject(emptyMap()) }
    val serviceTool =
      when (call.name) {
        "system_device_status" -> "device.status"
        "system_list_apps" -> "device.apps"
        "system_foreground_app" -> "device.foreground"
        "system_read_setting" -> "device.setting"
        "system_set_app_enabled" -> "device.set_app_enabled"
        else ->
          return ToolRunResult(
            ok = false,
            payload = "{\"ok\":false,\"error\":\"未知工具 ${call.name}\"}",
            elapsedMs = 0,
            log = "系统工具｜未知工具 ${call.name}",
            displayName = call.name,
            backend = "系统工具",
          )
      }
    val preparedArgs =
      if (serviceTool == "device.apps") {
        val requested = (args["limit"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val capped = (requested ?: 40).coerceIn(1, 100)
        JsonObject(args + ("limit" to kotlinx.serialization.json.JsonPrimitive(capped)))
      } else {
        args
      }
    val run =
      if (RootBridge.state.value.ready) RootBridge.execute(serviceTool, preparedArgs)
      else ShizukuBridge.execute(serviceTool, preparedArgs)
    val detail =
      when (serviceTool) {
        "device.status" -> "系统身份"
        "device.apps" ->
          "应用列表" +
            ((preparedArgs["query"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
              ?.takeIf { it.isNotBlank() }
              ?.let { " · $it" }
              .orEmpty())
        "device.foreground" -> "前台应用"
        "device.set_app_enabled" ->
          ((preparedArgs["package"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?.let { "应用开关 · $it" }
            ?: "应用开关")
        else -> "系统设置"
      }
    return ToolRunResult(
      ok = run.ok,
      payload = run.payload,
      elapsedMs = run.elapsedMs,
      log = "${run.backend}｜$detail · ${run.elapsedMs}ms" + if (run.ok) "" else " · 失败",
      displayName = call.name,
      backend = run.backend,
    )
  }
}
