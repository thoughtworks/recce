package recce.server.auth

import io.micronaut.http.HttpStatus.*
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class BasicAuthenticationProviderTest {

    @Inject
    lateinit var spec: RequestSpecification

    @Inject
    lateinit var authConfig: AuthConfiguration

    @Test
    fun `returns 200 OK when accessing a secured URL after authenticating`() {
        Given {
            spec(spec).auth().preemptive().basic(authConfig.username, authConfig.password)
        } When {
            get("/datasets")
        } Then {
            statusCode(OK.code)
        }
    }

    @Test
    fun `returns 401 UNAUTHORIZED when accessing a secured URL with invalid credentials`() {
        Given {
            spec(spec).auth().preemptive().basic("unknown", authConfig.password)
        } When {
            get("/datasets")
        } Then {
            statusCode(UNAUTHORIZED.code)
        }
    }

    @Test
    fun `returns 401 UNAUTHORIZED when accessing a secured URL without authenticating`() {
        Given {
            spec(spec)
        } When {
            get("/datasets")
        } Then {
            statusCode(UNAUTHORIZED.code)
        }
    }

    @Test
    fun `returns 403 FORBIDDEN when accessing a non-existent route after authenticating`() {
        Given {
            spec(spec).auth().preemptive().basic(authConfig.username, authConfig.password)
        } When {
            get("/some-non-existent-route")
        } Then {
            statusCode(FORBIDDEN.code)
        }
    }
}
