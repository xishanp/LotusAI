package ai.lotus.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProjectLinksTest {
  @Test
  fun keepsLinuxHubSourceAndLotusDistributionSeparate() {
    assertEquals("xishanp/Linuxhub", ProjectLinks.LinuxHubSourceRepo)
    assertEquals("xishanp/LotusAI", ProjectLinks.LotusDistributionRepo)
    assertNotEquals(ProjectLinks.LinuxHubSourceRepo, ProjectLinks.LotusDistributionRepo)
  }

  @Test
  fun updateCheckerUsesSignedDistributionRepository() {
    assertEquals(ProjectLinks.LotusDistributionRepo, UpdateChecker.Repo)
  }
}
