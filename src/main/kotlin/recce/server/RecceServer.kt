package recce.server

import io.micronaut.runtime.Micronaut.build
import reactor.tools.agent.ReactorDebugAgent

fun main(args: Array<String>) {
    ReactorDebugAgent.init();
    build()
        .banner(false)
        .args(*args)
        .packages("recce.server")
        .start()
}
