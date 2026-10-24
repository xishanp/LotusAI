package ai.lotus.app.shizuku

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import ai.lotus.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import rikka.shizuku.Shizuku
import java.lang.ref.WeakReference

enum class ShizukuPhase {
  Unavailable,
  PermissionRequired,
  Connecting,
  Ready,
  Error,
}

data class ShizukuState(
  val phase: ShizukuPhase = ShizukuPhase.Unavailable,
  val message: String = "未检测到 Shizuku",
  val uid: Int = -1,
  val identity: String = "",
  val serviceVersion: Int = 0,
) {
  val ready: Boolean get() = phase == ShizukuPhase.Ready
}

/**
 * App 进程侧的 Shizuku 生命周期。
 *
 * 注意：Shizuku 只把特权 Binder 交给本应用；真正执行系统工具的是独立进程
 * [LotusShizukuService]。任何一次模型工具调用只允许走一笔 [execute]。
 */
object ShizukuBridge {
  private const val PermissionRequestCode = 49371
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val json = Json { ignoreUnknownKeys = true }

  private val _state = MutableStateFlow(ShizukuState())
  val state: StateFlow<ShizukuState> = _state.asStateFlow()

  private var activityRef = WeakReference<Activity>(null)
  private var attached = false
  private var service: ILotusShizukuService? = null
  private var userServiceArgs: Shizuku.UserServiceArgs? = null

  private val binderReceived = Shizuku.OnBinderReceivedListener { onBinderReceived() }
  private val binderDead = Shizuku.OnBinderDeadListener { onBinderDead() }
  private val permissionResult =
    Shizuku.OnRequestPermissionResultListener { _, grantResult -> onPermissionResult(grantResult) }

  private val connection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val remote = binder?.let(ILotusShizukuService.Stub::asInterface)
        if (remote == null) {
          _state.value = ShizukuState(ShizukuPhase.Error, "Shizuku UserService 没有返回 Binder")
          return
        }
        service = remote
        scope.launch {
          runCatching {
              val raw = withContext(Dispatchers.IO) { remote.status() }
              val root = json.parseToJsonElement(raw).jsonObject
              val data = root["data"]?.jsonObject ?: root
              val uid = data["uid"]?.jsonPrimitive?.intOrNull ?: -1
              val identity = data["identity"]?.jsonPrimitive?.contentOrNull.orEmpty()
              _state.value =
                ShizukuState(
                  phase = ShizukuPhase.Ready,
                  message = "系统工具已连接",
                  uid = uid,
                  identity = identity,
                  serviceVersion = runCatching { Shizuku.getVersion() }.getOrDefault(0),
                )
            }
            .onFailure {
              service = null
              _state.value =
                ShizukuState(ShizukuPhase.Error, "UserService 启动失败：${it.message ?: it::class.simpleName ?: "未知错误"}")
            }
        }
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        _state.value = ShizukuState(ShizukuPhase.Connecting, "Shizuku UserService 已断开，正在重连")
        bindUserService()
      }
    }

  fun attach(activity: Activity) {
    activityRef = WeakReference(activity)
    if (!attached) {
      attached = true
      Shizuku.addBinderReceivedListenerSticky(binderReceived)
      Shizuku.addBinderDeadListener(binderDead)
      Shizuku.addRequestPermissionResultListener(permissionResult)
    }
    refresh()
  }

  fun detach(activity: Activity) {
    if (activityRef.get() === activity) activityRef.clear()
    if (attached) {
      Shizuku.removeBinderReceivedListener(binderReceived)
      Shizuku.removeBinderDeadListener(binderDead)
      Shizuku.removeRequestPermissionResultListener(permissionResult)
      attached = false
    }
  }

  fun refresh() {
    if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
      _state.value = ShizukuState(ShizukuPhase.Unavailable, "需要 Shizuku 11 或更高版本")
      return
    }
    if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
      _state.value = ShizukuState(ShizukuPhase.Unavailable, "未检测到 Shizuku 服务")
      return
    }
    when (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)) {
      PackageManager.PERMISSION_GRANTED -> bindUserService()
      else -> _state.value = ShizukuState(ShizukuPhase.PermissionRequired, "等待 Shizuku 授权")
    }
  }

  fun requestPermission() {
    if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
      _state.value = ShizukuState(ShizukuPhase.Unavailable, "未检测到 Shizuku 服务")
      return
    }
    runCatching { Shizuku.requestPermission(PermissionRequestCode) }
      .onFailure {
        _state.value = ShizukuState(ShizukuPhase.Error, "无法请求 Shizuku 授权：${it.message}")
      }
  }

  /** 只能由用户点击「连接 Shizuku」触发；冷启动绝不弹授权框。 */
  fun connectNow() {
    if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED) ==
      PackageManager.PERMISSION_GRANTED
    ) {
      refresh()
    } else {
      requestPermission()
    }
  }

  suspend fun execute(tool: String, args: JsonObject = JsonObject(emptyMap())): PrivilegedRun {
    val remote = service
      ?: return PrivilegedRun(false, "{\"ok\":false,\"error\":\"Shizuku 尚未连接\"}", 0L, tool, "Shizuku")
    val request =
      buildJsonObject {
          put("tool", tool)
          put("args", args)
        }
        .toString()
    val started = System.nanoTime()
    return withContext(Dispatchers.IO) {
      val raw = runCatching { remote.execute(request) }
        .getOrElse { "{\"ok\":false,\"error\":${jsonString(it.message ?: it::class.simpleName ?: "未知错误")}}" }
      val ok = runCatching {
          json.parseToJsonElement(raw).jsonObject["ok"]?.jsonPrimitive?.contentOrNull == "true"
        }
        .getOrDefault(false)
      PrivilegedRun(
        ok = ok,
        payload = raw,
        elapsedMs = (System.nanoTime() - started) / 1_000_000,
        tool = tool,
        backend = "Shizuku",
      )
    }
  }

  private fun onBinderReceived() {
    if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
      _state.value = ShizukuState(ShizukuPhase.Unavailable, "Shizuku 版本过低")
      return
    }
    refresh()
  }

  private fun onBinderDead() {
    service = null
    _state.value = ShizukuState(ShizukuPhase.Unavailable, "Shizuku 服务已停止")
  }

  private fun onPermissionResult(grantResult: Int) {
    if (grantResult == PackageManager.PERMISSION_GRANTED) {
      _state.value = ShizukuState(ShizukuPhase.Connecting, "已授权，正在启动工具服务")
      bindUserService()
    } else {
      _state.value = ShizukuState(ShizukuPhase.PermissionRequired, "Shizuku 授权被拒绝")
    }
  }

  private fun bindUserService() {
    if (service != null) return
    if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      _state.value = ShizukuState(ShizukuPhase.PermissionRequired, "等待 Shizuku 授权")
      return
    }
    val activity = activityRef.get()
    if (activity == null) {
      _state.value = ShizukuState(ShizukuPhase.Connecting, "等待前台 Activity 重新连接")
      return
    }
    val args =
      (userServiceArgs
          ?: Shizuku.UserServiceArgs(ComponentName(activity, LotusShizukuService::class.java))
            .daemon(false)
            .processNameSuffix("shizuku")
            .version(2)
            .debuggable(BuildConfig.DEBUG))
        .also { userServiceArgs = it }
    _state.value = ShizukuState(ShizukuPhase.Connecting, "正在启动 Shizuku 工具服务")
    runCatching { Shizuku.bindUserService(args, connection) }
      .onFailure {
        service = null
        _state.value = ShizukuState(ShizukuPhase.Error, "绑定 Shizuku 失败：${it.message}")
      }
  }

  private fun jsonString(value: String): String =
    buildString {
      append('"')
      value.forEach { c ->
        when (c) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
      }
      append('"')
    }
}
