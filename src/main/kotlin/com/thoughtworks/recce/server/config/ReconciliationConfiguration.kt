package com.thoughtworks.recce.server.config

import io.micronaut.context.annotation.ConfigurationProperties
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

@ConfigurationProperties("reconciliation")
class ReconciliationConfiguration {
    @NotEmpty
    lateinit var dataSets: Map<String, DataSetConfiguration>
}

class DataSetConfiguration {
    @NotNull
    lateinit var source: DataLoadDefinition

    @NotNull
    lateinit var target: DataLoadDefinition
}

class DataLoadDefinition {
    @NotBlank
    lateinit var dataSourceRef: String

    @NotBlank
    lateinit var query: String
}
