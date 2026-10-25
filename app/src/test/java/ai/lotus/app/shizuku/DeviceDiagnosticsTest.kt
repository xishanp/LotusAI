package ai.lotus.app.shizuku

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDiagnosticsTest {
  @Test
  fun parsesMemoryFromProcMeminfo() {
    val parsed =
      DeviceDiagnostics.parseMemInfoKb(
        """
        MemTotal:       11438804 kB
        MemFree:          274040 kB
        MemAvailable:    1957060 kB
        Cached:          1881092 kB
        SwapTotal:      10485756 kB
        SwapFree:         394440 kB
        """.trimIndent()
      )

    assertEquals(11438804L, parsed["MemTotal"])
    assertEquals(1957060L, parsed["MemAvailable"])
    assertEquals(10485756L, parsed["SwapTotal"])
  }

  @Test
  fun parsesLoadAverageAndProcessCounts() {
    assertEquals(
      listOf(7.66, 7.52, 7.34, 373.0, 16224.0),
      DeviceDiagnostics.parseLoadAverage("7.66 7.52 7.34 373/16224 23135"),
    )
  }

  @Test
  fun sortsProcessesByRssAndAppliesLimit() {
    val json =
      DeviceDiagnostics.parseProcessList(
        """
          PID  PPID USER            RSS NAME
         1000     1 system        12000 com.example.small
         1001     1 u0_a12        90000 com.example.large
         1002     1 root           5000 init
        """.trimIndent(),
        2,
      )

    val processes = json["processes"]!!.jsonArray
    assertEquals(3, json["total"]!!.jsonPrimitive.int)
    assertEquals(2, processes.size)
    assertEquals("com.example.large", processes[0].jsonObject["name"]!!.jsonPrimitive.content)
    assertEquals(90_000L * 1024L, processes[0].jsonObject["rssBytes"]!!.jsonPrimitive.long)
    assertTrue(json["truncated"]!!.jsonPrimitive.content.toBoolean())
  }

  @Test
  fun parsesThermalStatusAndTemperatures() {
    val json =
      DeviceDiagnostics.parseThermal(
        """
        Thermal Status: 2
        Cached temperatures:
          Temperature{mValue=30.7, mType=2, mName=battery, mStatus=0}
          Temperature{mValue=71.2, mType=0, mName=CPU0, mStatus=0}
        """.trimIndent()
      )

    assertEquals(2, json["thermalStatus"]!!.jsonPrimitive.int)
    assertEquals(2, json["temperatureCount"]!!.jsonPrimitive.int)
    assertEquals("battery", json["temperatures"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
  }
}
