package recce.server.api

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import recce.server.recrun.RecRun
import java.time.Instant

internal class RunApiModelTest {
    @Test
    fun `incomplete runs don't have duration`() {
        Assertions.assertThat(RunApiModel.Builder(RecRun(1, "empty", Instant.now())).build().completedDuration)
            .isNull()
    }
}
