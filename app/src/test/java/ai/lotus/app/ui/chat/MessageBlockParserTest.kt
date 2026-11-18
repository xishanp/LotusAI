package ai.lotus.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBlockParserTest {
  @Test
  fun splitsTextAndFencedCodeBlocks() {
    val raw =
      """
      先运行这个命令：

      ```bash
      ./gradlew assembleRelease
      echo done
      ```

      完成后告诉我结果。
      """.trimIndent()

    val blocks = parseMessageBlocks(raw)

    assertEquals(3, blocks.size)
    assertEquals("先运行这个命令：", (blocks[0] as MessageBlock.Text).content)
    val code = blocks[1] as MessageBlock.Code
    assertEquals("bash", code.language)
    assertEquals("./gradlew assembleRelease\necho done", code.content)
    assertEquals("完成后告诉我结果。", (blocks[2] as MessageBlock.Text).content)
  }

  @Test
  fun keepsUnclosedStreamingFenceAsCode() {
    val blocks = parseMessageBlocks("继续看：\n```kotlin\nfun main() = println(\"ok\")")

    assertEquals(2, blocks.size)
    val code = blocks[1] as MessageBlock.Code
    assertEquals("kotlin", code.language)
    assertTrue(code.content.contains("fun main()"))
  }

  @Test
  fun supportsMultipleCodeBlocks() {
    val blocks = parseMessageBlocks("```json\n{\"ok\":true}\n```\n中间\n~~~sh\necho hi\n~~~")

    assertEquals(3, blocks.size)
    assertEquals("json", (blocks[0] as MessageBlock.Code).language)
    assertEquals("中间", (blocks[1] as MessageBlock.Text).content)
    assertEquals("sh", (blocks[2] as MessageBlock.Code).language)
  }
}

class InlineMarkdownTest {
  @Test
  fun malformedFeishuLinkIsRenderedAsLabelNotMarkdownJunk() {
    val raw = "看这里 [feishu.cn]\\([https://www.feishu.cn/hc/zh-CN/articles/1?utm_source=openai))"
    val rendered = formatInlineMarkdown(raw).text
    assertTrue(rendered.contains("看这里 feishu.cn"))
    assertFalse(rendered.contains("]("))
    assertFalse(rendered.contains("\\("))
  }

  @Test
  fun boldAndInlineCodeLoseTheirMarkers() {
    val rendered = formatInlineMarkdown("**Kotlin** 使用 `MAX_LINES`。").text
    assertEquals("Kotlin 使用 MAX_LINES。", rendered)
  }

  @Test
  fun inlineCodeNestedInsideBoldLosesBackticks() {
    val rendered =
      formatInlineMarkdown("**Kotlin（JVM 11+，新增一条记录；`Name` 须为现有文本字段）：**").text
    assertEquals("Kotlin（JVM 11+，新增一条记录；Name 须为现有文本字段）：", rendered)
    assertFalse(rendered.contains("`"))
  }
}
