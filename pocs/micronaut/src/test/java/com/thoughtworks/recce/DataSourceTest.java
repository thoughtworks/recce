package com.thoughtworks.recce;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public class DataSourceTest {

    @Inject
    @Named("source")
    JdbcOperations sourceOperations;

    @Inject
    @Named("target")
    JdbcOperations targetOperations;

    @Test
    public void shouldInjectDataSources() {
        assertThat(countRows(sourceOperations)).isEqualTo(3);
        assertThat(countRows(targetOperations)).isEqualTo(4);
    }

    private Integer countRows(JdbcOperations operations) {
        return operations.prepareStatement("select count(*) from testdata", preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.first() ? resultSet.getInt(1) : -1;
        });
    }
}
