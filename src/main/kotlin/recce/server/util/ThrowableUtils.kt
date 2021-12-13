package recce.server.util

import com.google.common.base.Throwables

object ThrowableUtils {
    fun extractFailureCause(throwable: Throwable): String {
        val rootCause = Throwables.getRootCause(throwable)
        return if (rootCause.equals(throwable)) {
            throwable.messageOrClassName()
        } else {
            "${throwable.messageOrClassName()}, rootCause=[${rootCause.messageOrClassName()}]"
        }
    }

    private fun Throwable.messageOrClassName(): String = this.message ?: this.javaClass.simpleName
}
