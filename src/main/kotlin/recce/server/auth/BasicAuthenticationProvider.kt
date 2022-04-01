package recce.server.auth

import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink

@Singleton
class BasicAuthenticationProvider(private val authConfiguration: AuthConfiguration) : AuthenticationProvider {
    override fun authenticate(
        httpRequest: HttpRequest<*>?,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> =
        Flux.create({ emitter: FluxSink<AuthenticationResponse> ->
            if (authenticationRequest.identity == authConfiguration.username && authenticationRequest.secret == authConfiguration.password) {
                emitter.next(AuthenticationResponse.success(authenticationRequest.identity as String))
                emitter.complete()
            } else {
                emitter.error(AuthenticationResponse.exception())
            }
        }, FluxSink.OverflowStrategy.ERROR)
}
