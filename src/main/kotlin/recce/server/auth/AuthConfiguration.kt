package recce.server.auth

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Singleton
@ConfigurationProperties("auth")
class AuthConfiguration @ConfigurationInject constructor(@NotBlank val username: String, @NotBlank val password: String)
