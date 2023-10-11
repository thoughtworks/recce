package recce.server

import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.server.EmbeddedServer
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.JsonConfig
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.http.ContentType
import io.restassured.path.json.config.JsonPathConfig
import io.restassured.specification.RequestSpecification
import jakarta.inject.Singleton

@Factory
class TestConfig {
    @Singleton
    fun restAssuredSpec(server: EmbeddedServer): RequestSpecification {
        // Yes, this is static configuration; seems no other way to do it with RestAssured
        RestAssured.config =
            RestAssured.config()
                .jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.DOUBLE))

        return RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setBaseUri(server.uri)
            .addFilter(ResponseLoggingFilter())
            .build()
    }
}
