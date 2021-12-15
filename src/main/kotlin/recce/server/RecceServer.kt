package recce.server

import io.micronaut.runtime.Micronaut.build
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import reactor.tools.agent.ReactorDebugAgent

private const val gitHubProject = "https://github.com/ThoughtWorks-SEA/recce"

@OpenAPIDefinition(
    info = Info(
        title = "Recce Server",
        description = "Server-based database reconciliation tool for developers",
        contact = Contact(name = "Recce Community", url = "$gitHubProject/issues")
    ),
    externalDocs = ExternalDocumentation(description = "Server Docs", url = "$gitHubProject/README.md")
)
object RecceServer {

    @JvmStatic
    fun main(args: Array<String>) {
        ReactorDebugAgent.init()
        build()
            .banner(false)
            .args(*args)
            .packages("recce.server")
            .start()
    }
}
