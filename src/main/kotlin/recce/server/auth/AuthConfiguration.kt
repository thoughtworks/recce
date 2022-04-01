package recce.server.auth

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Singleton

@Singleton
@ConfigurationProperties("auth")
class AuthConfiguration {
    lateinit var username: String
    lateinit var password: String
}
