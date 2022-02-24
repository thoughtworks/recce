package recce.server

import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton

@Singleton
class BuildInfoConfiguration {
    @field:Property(name = "version")
    lateinit var version: String
}
