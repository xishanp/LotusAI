package ai.lotus.app.shizuku

import ai.lotus.app.data.ChatToolCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuToolsPolicyTest {
  private val shellName = "system_shell_exec"

  @Test
  fun restrictedPolicyDoesNotExposeArbitraryShell() {
    assertFalse(ShizukuTools.specs(fullAccess = false).any { it.name == shellName })
  }

  @Test
  fun fullAccessPolicyExposesArbitraryShell() {
    assertTrue(ShizukuTools.specs(fullAccess = true).any { it.name == shellName })
  }

  @Test
  fun recognizesAndExtractsShellCommand() {
    val call =
      ChatToolCall(
        id = "shell-1",
        name = shellName,
        arguments = """{"command":"  free -h\ncat /proc/meminfo  ","timeout_seconds":10}""",
      )

    assertTrue(ShizukuTools.isShellExecution(call))
    assertEquals("free -h\ncat /proc/meminfo", ShizukuTools.shellCommand(call))
    assertFalse(ShizukuTools.isShellExecution(call.copy(name = "system_diagnostics")))
  }

  @Test
  fun shellCannotRunWithoutOneTimeApproval() = runBlocking {
    val result =
      ShizukuTools.execute(
        ChatToolCall(id = "shell-2", name = shellName, arguments = """{"command":"id"}"""),
      )

    assertFalse(result.ok)
    assertTrue(result.payload.contains("尚未获得用户批准"))
  }

  @Test
  fun blankShellCommandIsRejectedBeforeBackendExecution() = runBlocking {
    val result =
      ShizukuTools.execute(
        ChatToolCall(id = "shell-3", name = shellName, arguments = """{"command":"   "}"""),
        shellApproved = true,
      )

    assertFalse(result.ok)
    assertTrue(result.payload.contains("命令不能为空"))
  }

  @Test
  fun oversizedShellCommandIsRejectedBeforeBackendExecution() = runBlocking {
    val command = "x".repeat(32_769)
    val result =
      ShizukuTools.execute(
        ChatToolCall(
          id = "shell-4",
          name = shellName,
          arguments = """{"command":"$command"}""",
        ),
        shellApproved = true,
      )

    assertFalse(result.ok)
    assertTrue(result.payload.contains("超过 32768 字符"))
  }
}
