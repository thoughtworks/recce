package recce.server.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ThrowableUtilsTest {
    private val rootCause = IllegalArgumentException("root")
    private val causedByRoot = IllegalArgumentException("causedByRoot", rootCause)
    private val causedByCausedBy =
        IllegalArgumentException(
            "causedByCausedByRoot" +
                "",
            causedByRoot
        )

    @Test
    fun `should return just message for throwable with no cause`() {
        assertThat(ThrowableUtils.extractFailureCause(rootCause))
            .isEqualTo("root")
    }

    @Test
    fun `should return message with root cause for throwable with cause`() {
        assertThat(ThrowableUtils.extractFailureCause(causedByRoot))
            .isEqualTo("causedByRoot, rootCause=[root]")
    }

    @Test
    fun `should ignore intermediary exceptions`() {
        assertThat(ThrowableUtils.extractFailureCause(causedByCausedBy))
            .isEqualTo("causedByCausedByRoot, rootCause=[root]")
    }

    @Test
    fun `should return exception type if there is no message`() {
        val noMessageRoot = IllegalArgumentException()
        assertThat(ThrowableUtils.extractFailureCause(noMessageRoot))
            .isEqualTo("IllegalArgumentException")
    }

    @Test
    fun `should return exception type if there is no message for throwable with cause`() {
        val noMessageRoot = IllegalArgumentException()
        val bad = IllegalCallerException("bad", noMessageRoot)
        assertThat(ThrowableUtils.extractFailureCause(bad))
            .isEqualTo("bad, rootCause=[IllegalArgumentException]")
    }
}
