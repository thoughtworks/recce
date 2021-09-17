package com.thoughtworks.recce;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.db.api.Assertions.assertThat;

@MicronautTest
public class DataSourceTest {

    @Inject @Named("source")
    DataSource sourceData;

    @Inject @Named("target")
    DataSource targetData;

    @Test
    public void shouldInjectDataSources() {
        assertThat(new Table(sourceData, "TESTDATA")).hasNumberOfRows(3);
        assertThat(new Table(targetData, "TESTDATA")).hasNumberOfRows(3);
    }
}
