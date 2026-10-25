package ai.lotus.app.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmApiErrorTest {
  @Test
  fun gatewayHtmlIsReducedToReadableStatusAndRayId() {
    val html = """
      <!DOCTYPE html>
      <html>
        <head><title>mzhcloud | 502: Bad gateway</title></head>
        <body>Cloudflare Ray ID: 8f12ab34cd56ef78</body>
      </html>
    """.trimIndent()

    val message = upstreamHttpError(502, "text/html; charset=UTF-8", html)

    assertEquals("上游网关 502，服务暂不可用（mzhcloud | 502: Bad gateway） · Ray ID 8f12ab34cd56ef78", message)
    assertFalse(message.contains("<!DOCTYPE", ignoreCase = true))
    assertFalse(message.contains("<html", ignoreCase = true))
  }

  @Test
  fun jsonErrorKeepsOnlyTheUpstreamMessage() {
    val body = """{"error":{"message":"model is temporarily unavailable","type":"server_error"}}"""

    val message = upstreamHttpError(503, "application/json", body)

    assertTrue(message.startsWith("上游服务暂不可用"))
    assertTrue(message.contains("model is temporarily unavailable"))
    assertFalse(message.contains("server_error"))
  }

  @Test
  fun emptyGatewayResponseStillGetsAUsefulMessage() {
    assertEquals("上游网关 502，服务暂不可用", upstreamHttpError(502, null, ""))
  }

  @Test
  fun oneAssistantResponseCanCarryMultipleToolCalls() = runBlocking {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v1/chat/completions") { exchange ->
      val body =
        """
        data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"desktop_start","arguments":"{}"}},{"index":1,"id":"call_2","type":"function","function":{"name":"desktop_status","arguments":"{}"}}]},"finish_reason":null}]}

        data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

        data: [DONE]

        """.trimIndent().toByteArray()
      exchange.responseHeaders.add("Content-Type", "text/event-stream")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    server.start()
    try {
      val events =
        LlmApi.chatStream(
            baseUrl = "http://127.0.0.1:${server.address.port}/v1",
            apiKey = "test",
            model = "test-model",
            turns = listOf(ChatTurn(role = "user", content = "start and check")),
          )
          .toList()
      val calls = events.filterIsInstance<ChatEvent.ToolCalls>().single().calls
      assertEquals(listOf("call_1", "call_2"), calls.map { it.id })
      assertEquals(listOf("desktop_start", "desktop_status"), calls.map { it.name })
    } finally {
      server.stop(0)
    }
  }
}
