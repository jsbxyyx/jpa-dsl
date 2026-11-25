package io.github.jsbxyyx.jdbcdsl.dialect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DialectDetectorTest {
    @ParameterizedTest
    @CsvSource({
        "MySQL, MySqlDialect",
        "MariaDB, MySqlDialect",
        "PostgreSQL, PostgresDialect",
        "Microsoft SQL Server, SqlServerDialect",
        "H2, H2Dialect",
        "Oracle, OracleDialect",
        "DM DBMS, DmDialect",
        "KingbaseES, KingbaseDialect"
    })
    void recognizesKnownProductsAndClosesConnection(String product, String dialectName) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(product);

        assertThat(DialectDetector.detect(dataSource).getClass().getSimpleName())
                .isEqualTo(dialectName);
        verify(connection).close();
    }

    @Test
    void registeredProviderTakesPriorityOverBuiltInDetection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL SPI override");

        assertThat(DialectDetector.detect(dataSource)).isInstanceOf(H2Dialect.class);
        verify(connection).close();
    }

    @Test
    void missingDataSourceFails() {
        assertThatThrownBy(() -> DialectDetector.detect(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DataSource")
                .hasMessageContaining("explicitly");
    }

    @Test
    void connectionFailurePreservesCause() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLException failure = new SQLException("database unavailable");
        when(dataSource.getConnection()).thenThrow(failure);

        assertThatThrownBy(() -> DialectDetector.detect(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicitly")
                .hasCause(failure);
    }

    @Test
    void metadataFailurePreservesCauseAndClosesConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("metadata unavailable");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenThrow(failure);

        assertThatThrownBy(() -> DialectDetector.detect(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(failure);
        verify(connection).close();
    }

    @Test
    void missingMetadataFailsAndClosesConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        assertThatThrownBy(() -> DialectDetector.detect(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata");
        verify(connection).close();
    }

    @Test
    void unknownOrMissingProductFailsAndClosesConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("unknown", null, " ");

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> DialectDetector.detect(dataSource))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("explicitly");
        }
        verify(connection, times(3)).close();
    }
}
