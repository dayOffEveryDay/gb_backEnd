package com.costco.gb.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BusinessLogTest {
    private final Logger logger = mock(Logger.class);
    private final TransactionTemplate transaction = new TransactionTemplate(new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object tx, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    });

    @Test
    void successIsLoggedOnlyAfterCommitWithOriginalArguments() {
        Object[] arguments = {7L};
        transaction.executeWithoutResult(status -> {
            BusinessLog.info(logger, "userId={}", arguments);
            arguments[0] = 8L;
            verifyNoInteractions(logger);
        });
        verify(logger).info("userId={}", new Object[]{7L});
    }

    @Test
    void rollbackSuppressesSuccessAndReportsFilesystemReconciliation() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            BusinessLog.info(logger, "event=CREATED");
            BusinessLog.warnOnRollback(logger, "event=FILE_DB_MISMATCH id={}", 7L);
            throw new IllegalStateException("rollback");
        }));
        verify(logger).warn("event=FILE_DB_MISMATCH id={}", new Object[]{7L});
        verifyNoMoreInteractions(logger);
    }

    @Test
    void commitDoesNotReportFilesystemMismatch() {
        transaction.executeWithoutResult(status ->
                BusinessLog.warnOnRollback(logger, "event=FILE_DB_MISMATCH"));
        verifyNoInteractions(logger);
    }

    @Test
    void nonTransactionalOperationsLogImmediately() {
        BusinessLog.info(logger, "event=CREATED id={}", 7L);
        verify(logger).info("event=CREATED id={}", new Object[]{7L});
    }
}
