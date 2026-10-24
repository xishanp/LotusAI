package ai.lotus.app.shizuku

/** 系统工具共用的安全边界。Root 与 Shizuku 两个后端必须使用同一套规则。 */
object SystemToolSafety {
  /** Android 包名不使用空格、斜杠和 shell 元字符；这里再做一次严格限制。 */
  val packageNamePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")

  /** 设置键只允许常见字符，避免把用户输入拼进 shell。 */
  val settingKeyPattern = Regex("^[A-Za-z0-9_.:-]+$")

  /** 冻结这些包会直接影响系统交互、电话、输入法、应用管理或桌面。 */
  val criticalPackages =
    setOf(
      "android",
      "com.android.systemui",
      "com.android.settings",
      "com.android.packageinstaller",
      "com.google.android.packageinstaller",
      "com.android.permissioncontroller",
      "com.google.android.permissioncontroller",
      "com.android.inputmethod.latin",
      "com.google.android.inputmethod.latin",
      "com.android.phone",
      "com.android.bluetooth",
      "com.android.launcher3",
      "com.google.android.apps.nexuslauncher",
    )

  fun requireSafePackage(packageName: String) {
    require(packageNamePattern.matches(packageName)) { "非法包名：$packageName" }
    require(packageName !in criticalPackages) { "拒绝操作系统关键应用：$packageName" }
  }
}
