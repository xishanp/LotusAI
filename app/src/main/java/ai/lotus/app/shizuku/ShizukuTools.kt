package ai.lotus.app.shizuku

import ai.lotus.app.data.ChatToolCall
import ai.lotus.app.data.ChatToolSpec
import ai.lotus.app.data.ToolRunResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    - 只有用户明确要求读取设备信息、应用列表、前台应用、系统设置或运行诊断时才调用读取工具。
    - 只有用户明确点名某个非关键系统应用并要求冻结或解冻时，才允许调用 system_set_app_enabled。
    - 可以连续调用多个工具：先观察上一次工具结果，再决定下一步；独立的读取可以同一轮发起。
    - 任务完成后停止调用工具并给出最终结果；不要为了展示能力而重复调用。
    - 不得声称执行过没有实际执行的系统操作。工具失败时要如实说明。
    - 工具成功后，回答第一句固定用「已完成：」，直接说对象和新状态；禁止使用「确实、应该、可能」这类不确定措辞。
    - 工具失败后，回答第一句固定用「未完成：」，再说明失败原因。
    - 系统诊断只接受固定类别（内存、CPU、存储、电池、网络、进程、温度）；受限模式不提供任意 shell，完全访问模式只能通过逐次审批的 system_shell_exec 执行。系统关键应用永远禁止冻结。
    - Root 后端优先；Root 未连接时才使用 Shizuku。不要虚报当前后端。
    """.trimIndent()

  val fullAccessPolicy: String =
    """
    完全访问命令规则：
    - system_shell_exec 可用，但每个命令都必须先等待用户在界面上批准；没有批准时绝不能声称命令已执行。
    - 先在命令参数里写清真正要做的事，不要用会掩盖风险的重定向或编码把命令藏起来。
    - 用户拒绝后立即停止这条路径，可用只读诊断工具完成剩余工作；不要换一条命令绕过拒绝。
    - 只执行当前任务需要的命令，不主动扩大范围。
    """.trimIndent()

  private const val ShellExecTool = "system_shell_exec"
  private const val MaxShellCommandChars = 32_768
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
        name = "system_diagnostics",
        description =
          "读取本机实时诊断数据，只读。memory=内存用量与 swap；cpu=负载和 CPU 时间；storage=数据盘空间；battery=电量、电流、温度；network=当前网络、接口、地址与 DNS；processes=按 RSS 排序的进程；thermal=系统温度。用户问“剩余内存/为什么卡/存储还剩多少/谁占内存/设备发热”时直接调用，不要要求用户再开终端。",
        parameters =
          buildJsonObject {
            put("type", "object")
            put(
              "properties",
              buildJsonObject {
                put(
                  "category",
                  buildJsonObject {
                    put("type", "string")
                    put(
                      "enum",
                      kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("memory"))
                        add(kotlinx.serialization.json.JsonPrimitive("cpu"))
                        add(kotlinx.serialization.json.JsonPrimitive("storage"))
                        add(kotlinx.serialization.json.JsonPrimitive("battery"))
                        add(kotlinx.serialization.json.JsonPrimitive("network"))
                        add(kotlinx.serialization.json.JsonPrimitive("processes"))
                        add(kotlinx.serialization.json.JsonPrimitive("thermal"))
                      },
                    )
                  },
                )
                put(
                  "limit",
                  buildJsonObject {
                    put("type", "integer")
                    put("description", "processes 类别返回的进程数量，1-100，默认 20")
                    put("minimum", 1)
                    put("maximum", 100)
                  },
                )
              },
            )
            put("required", kotlinx.serialization.json.buildJsonArray {
              add(kotlinx.serialization.json.JsonPrimitive("category"))
            })
            put("additionalProperties", false)
          },
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

  /** 任意 shell 的声明只在用户开启「完全访问」后注入；执行前仍需逐次审批。 */
  val fullAccessSpec: ChatToolSpec =
    ChatToolSpec(
      name = ShellExecTool,
      description =
        "在 Root 或 Shizuku 的特权身份执行任意 shell 命令。每次执行都会暂停工具循环并请求用户批准，拒绝后不会运行。适合固定诊断工具覆盖不到的命令；优先使用 system_diagnostics。",
      parameters =
        buildJsonObject {
          put("type", "object")
          put(
            "properties",
            buildJsonObject {
              put(
                "command",
                buildJsonObject {
                  put("type", "string")
                  put("description", "要执行的完整 shell 命令；最多 32768 字符")
                },
              )
              put(
                "timeout_seconds",
                buildJsonObject {
                  put("type", "integer")
                  put("description", "执行超时，1-120 秒，默认 30")
                  put("minimum", 1)
                  put("maximum", 120)
                },
              )
            },
          )
          put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("command"))
          })
          put("additionalProperties", false)
        },
    )

  fun specs(fullAccess: Boolean): List<ChatToolSpec> =
    if (fullAccess) specs + fullAccessSpec else specs

  fun isShellExecution(call: ChatToolCall): Boolean = call.name == ShellExecTool

  fun shellCommand(call: ChatToolCall): String {
    val args =
      runCatching { json.parseToJsonElement(call.arguments).jsonObject }.getOrElse {
        JsonObject(emptyMap())
      }
    return args.string("command").trim()
  }

  fun shellBackendLabel(): String? =
    when {
      RootBridge.state.value.ready -> "Root"
      ShizukuBridge.state.value.ready -> "Shizuku"
      else -> null
    }

  /**
   * 这一轮是否有可用的特权后端。Root 是应用自己的 su 后端；Shizuku 是兼容后端。
   * 两者都不在时，模型请求里不会声明系统工具。
   */
  fun ready(): Boolean = RootBridge.state.value.ready || ShizukuBridge.state.value.ready

  suspend fun execute(call: ChatToolCall, shellApproved: Boolean = false): ToolRunResult {
    val args = runCatching { json.parseToJsonElement(call.arguments).jsonObject }.getOrElse { JsonObject(emptyMap()) }
    if (call.name == ShellExecTool && !shellApproved) {
      return ToolRunResult(
        ok = false,
        payload = "{\"ok\":false,\"error\":\"完全访问命令尚未获得用户批准\"}",
        elapsedMs = 0,
        log = "系统工具｜命令未获批准，已阻止执行",
        displayName = call.name,
        backend = "系统工具",
      )
    }
    val serviceTool =
      when (call.name) {
        "system_device_status" -> "device.status"
        "system_diagnostics" -> "device.diagnostics"
        "system_shell_exec" -> "device.shell_exec"
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
      when (serviceTool) {
        "device.apps" -> {
          val requested =
            (args["limit"] as? kotlinx.serialization.json.JsonPrimitive)
              ?.contentOrNull
              ?.toIntOrNull()
          val capped = (requested ?: 40).coerceIn(1, 100)
          JsonObject(args + ("limit" to kotlinx.serialization.json.JsonPrimitive(capped)))
        }
        "device.shell_exec" -> {
          val command = args.string("command").trim()
          if (command.isBlank()) {
            return ToolRunResult(
              ok = false,
              payload = "{\"ok\":false,\"error\":\"命令不能为空\"}",
              elapsedMs = 0,
              log = "系统工具｜完全访问命令为空",
              displayName = call.name,
              backend = shellBackendLabel() ?: "系统工具",
            )
          }
          if (command.length > MaxShellCommandChars) {
            return ToolRunResult(
              ok = false,
              payload = "{\"ok\":false,\"error\":\"命令超过 32768 字符\"}",
              elapsedMs = 0,
              log = "系统工具｜完全访问命令过长，已阻止",
              displayName = call.name,
              backend = shellBackendLabel() ?: "系统工具",
            )
          }
          val timeout =
            (args["timeout_seconds"] as? kotlinx.serialization.json.JsonPrimitive)
              ?.contentOrNull
              ?.toIntOrNull()
              ?.coerceIn(1, 120)
              ?: 30
          JsonObject(
            args +
              ("command" to kotlinx.serialization.json.JsonPrimitive(command)) +
              ("timeoutMs" to kotlinx.serialization.json.JsonPrimitive(timeout * 1000))
          )
        }
        else -> args
      }
    val run =
      if (RootBridge.state.value.ready) RootBridge.execute(serviceTool, preparedArgs)
      else ShizukuBridge.execute(serviceTool, preparedArgs)
    val detail =
      when (serviceTool) {
        "device.status" -> "系统身份"
        "device.diagnostics" ->
          ((preparedArgs["category"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?.let { "系统诊断 · $it" }
            ?: "系统诊断")
        "device.shell_exec" ->
          "完全访问命令 · " + preparedArgs.string("command").lineSequence().firstOrNull().orEmpty().take(90)
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

  private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}
