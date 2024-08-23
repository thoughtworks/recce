package recce.server.auth

import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationFailureReason
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.security.MessageDigest

@Singleton
class BasicAuthenticationProvider(private val authConfiguration: AuthConfiguration) : AuthenticationProvider {
    override fun authenticate(
        httpRequest: HttpRequest<*>?,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> =
        Flux.create({ emitter: FluxSink<AuthenticationResponse> ->
            if (
                authConfiguration.username.constantTimeEquals(authenticationRequest.identity) &&
                authConfiguration.password.constantTimeEquals(authenticationRequest.secret)
            ) {
                emitter.next(AuthenticationResponse.success(authenticationRequest.identity as String))
                emitter.complete()
            } else {
                emitter.error(AuthenticationResponse.exception(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH))
            }
        }, FluxSink.OverflowStrategy.ERROR)
}

fun String?.constantTimeEquals(other: Any?): Boolean {
    if (this == null || other !is String) {
        return this == other
    }
    return MessageDigest.isEqual(this.toByteArray(), other.toByteArray())
}
