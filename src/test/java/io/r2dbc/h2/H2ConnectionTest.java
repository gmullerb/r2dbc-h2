/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.r2dbc.h2;

import io.r2dbc.h2.client.Client;
import io.r2dbc.h2.codecs.MockCodecs;
import io.r2dbc.spi.R2dbcNonTransientException;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import io.r2dbc.spi.R2dbcRollbackException;
import org.h2.engine.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransactionRollbackException;
import java.util.Collections;

import static io.r2dbc.spi.IsolationLevel.READ_COMMITTED;
import static io.r2dbc.spi.IsolationLevel.READ_UNCOMMITTED;
import static io.r2dbc.spi.IsolationLevel.REPEATABLE_READ;
import static io.r2dbc.spi.IsolationLevel.SERIALIZABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class H2ConnectionTest {

    private final Client client = mock(Client.class, RETURNS_SMART_NULLS);

    @BeforeEach
    void setUp() {
        when(this.client.prepareCommand("CALL H2VERSION()", Collections.emptyList())).thenReturn(Collections.emptyIterator());
    }

    @Test
    void beginTransaction() {
        when(this.client.inTransaction()).thenReturn(false);

        new H2Connection(this.client, MockCodecs.empty())
            .beginTransaction()
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    @Disabled("see https://github.com/r2dbc/r2dbc-h2/issues/83")
    void beginTransactionErrorResponse() {
        when(this.client.inTransaction()).thenReturn(false);
        doThrow(new SQLNonTransientConnectionException("Unable to disable autocommits", "some state", 999)).when(this.client).disableAutoCommit();

        new H2Connection(this.client, MockCodecs.empty())
            .beginTransaction()
            .as(StepVerifier::create)
            .verifyErrorMatches(R2dbcNonTransientResourceException.class::isInstance);
    }

    @Test
    void beginTransactionInTransaction() {
        when(this.client.inTransaction()).thenReturn(true);
        verifyNoMoreInteractions(this.client);

        new H2Connection(this.client, MockCodecs.empty())
            .beginTransaction()
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void close() {
        when(this.client.close()).thenReturn(Mono.empty());

        new H2Connection(this.client, MockCodecs.empty())
            .close()
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void commitTransaction() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .commitTransaction()
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    @Disabled("see https://github.com/r2dbc/r2dbc-h2/issues/83")
    void commitTransactionErrorResponse() {
        when(this.client.inTransaction()).thenReturn(true);
        doThrow(new SQLTransactionRollbackException("can't commit", "some state", 999)).when(this.client).execute("COMMIT");

        new H2Connection(this.client, MockCodecs.empty())
            .commitTransaction()
            .as(StepVerifier::create)
            .verifyErrorMatches(R2dbcRollbackException.class::isInstance);
    }

    @Test
    void commitTransactionNonOpen() {
        when(this.client.inTransaction()).thenReturn(false);
        verifyNoMoreInteractions(this.client);

        new H2Connection(this.client, MockCodecs.empty())
            .commitTransaction()
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void constructorNoClient() {
        assertThatIllegalArgumentException().isThrownBy(() -> new H2Connection(null, MockCodecs.empty()))
            .withMessage("client must not be null");
    }

    @Test
    void constructorNoCodecs() {
        assertThatIllegalArgumentException().isThrownBy(() -> new H2Connection(this.client, null))
            .withMessage("codecs must not be null");
    }

    @Test
    void createBatch() {
        assertThat(new H2Connection(this.client, MockCodecs.empty()).createBatch()).isInstanceOf(H2Batch.class);
    }

    @Test
    void createSavepoint() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .createSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    @Disabled("see https://github.com/r2dbc/r2dbc-h2/issues/83")
    void createSavepointErrorResponse() {
        when(this.client.inTransaction()).thenReturn(true);
        doThrow(new SQLFeatureNotSupportedException("can't savepoint", "some state", 999)).when(this.client).execute("SAVEPOINT test-name");

        new H2Connection(this.client, MockCodecs.empty())
            .createSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyErrorMatches(R2dbcNonTransientException.class::isInstance);
    }

    @Test
    void createSavepointNoName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new H2Connection(this.client, MockCodecs.empty()).createSavepoint(null))
            .withMessage("name must not be null");
    }

    @Test
    void createSavepointNonOpen() {
        when(this.client.inTransaction()).thenReturn(false);
        verifyNoMoreInteractions(this.client);

        new H2Connection(this.client, MockCodecs.empty())
            .createSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void createStatement() {
        assertThat(new H2Connection(this.client, MockCodecs.empty()).createStatement("test-query-?")).isInstanceOf(H2Statement.class);
    }

    @Test
    void releaseSavepoint() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .releaseSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    @Disabled("see https://github.com/r2dbc/r2dbc-h2/issues/83")
    void releaseSavepointErrorResponse() {
        when(this.client.inTransaction()).thenReturn(true);
        doThrow(new SQLFeatureNotSupportedException("can't savepoint", "some state", 999)).when(this.client).execute("RELEASE SAVEPOINT test-name");

        new H2Connection(this.client, MockCodecs.empty())
            .releaseSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyErrorMatches(R2dbcNonTransientException.class::isInstance);
    }

    @Test
    void releaseSavepointNoName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new H2Connection(this.client, MockCodecs.empty()).releaseSavepoint(null))
            .withMessage("name must not be null");
    }

    @Test
    void releaseSavepointNonOpen() {
        when(this.client.inTransaction()).thenReturn(false);
        verifyNoMoreInteractions(this.client);

        new H2Connection(this.client, MockCodecs.empty())
            .releaseSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void rollbackTransaction() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .rollbackTransaction()
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    @Disabled("see https://github.com/r2dbc/r2dbc-h2/issues/83")
    void rollbackTransactionErrorResponse() {
        when(this.client.inTransaction()).thenReturn(true);
        doThrow(new SQLFeatureNotSupportedException("can't savepoint", "some state", 999)).when(this.client).execute("ROLLBACK");

        new H2Connection(this.client, MockCodecs.empty())
            .rollbackTransaction()
            .as(StepVerifier::create)
            .verifyErrorMatches(R2dbcRollbackException.class::isInstance);
    }

    @Test
    void rollbackTransactionNonOpen() {
        when(this.client.inTransaction()).thenReturn(false);
        verifyNoMoreInteractions(this.client);

        new H2Connection(this.client, MockCodecs.empty())
            .rollbackTransaction()
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void rollbackTransactionToSavepoint() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .rollbackTransactionToSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    @Disabled("see https://github.com/r2dbc/r2dbc-h2/issues/83")
    void rollbackTransactionToSavepointErrorResponse() {
        when(this.client.inTransaction()).thenReturn(true);
        doThrow(new SQLFeatureNotSupportedException("can't savepoint", "some state", 999)).when(this.client).execute("ROLLBACK TO SAVEPOINT test-name");

        new H2Connection(this.client, MockCodecs.empty())
            .rollbackTransactionToSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyErrorMatches(R2dbcRollbackException.class::isInstance);
    }

    @Test
    void rollbackTransactionToSavepointNoName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new H2Connection(this.client, MockCodecs.empty()).rollbackTransactionToSavepoint(null))
            .withMessage("name must not be null");
    }

    @Test
    void rollbackTransactionToSavepointNonOpen() {
        when(this.client.inTransaction()).thenReturn(false);
        verifyNoMoreInteractions(this.client);

        new H2Connection(this.client, MockCodecs.empty())
            .rollbackTransactionToSavepoint("test-name")
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void setTransactionIsolationLevel() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .setTransactionIsolationLevel(READ_COMMITTED)
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void setTransactionIsolationLevelReadUncommitted() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .setTransactionIsolationLevel(READ_UNCOMMITTED)
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void setTransactionIsolationLevelRepeatableRead() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .setTransactionIsolationLevel(REPEATABLE_READ)
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void setTransactionIsolationLevelSerializable() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .setTransactionIsolationLevel(SERIALIZABLE)
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void getConnectionMetadata() {
        H2ConnectionMetadata metadata = new H2Connection(this.client, MockCodecs.empty()).getMetadata();

        assertThat(metadata.getDatabaseProductName()).isEqualTo("H2");
        assertThat(metadata.getDatabaseVersion()).isEqualTo(Constants.getVersion());
    }

    @Disabled("Not yet implemented")
    @Test
    void setTransactionIsolationLevelErrorResponse() {
        when(this.client.inTransaction()).thenReturn(true);

        new H2Connection(this.client, MockCodecs.empty())
            .setTransactionIsolationLevel(READ_COMMITTED)
            .as(StepVerifier::create)
            .verifyErrorMatches(R2dbcNonTransientException.class::isInstance);
    }

    @Test
    void setTransactionIsolationLevelNoIsolationLevel() {
        assertThatIllegalArgumentException().isThrownBy(() -> new H2Connection(this.client, MockCodecs.empty()).setTransactionIsolationLevel(null))
            .withMessage("isolationLevel must not be null");
    }

}
