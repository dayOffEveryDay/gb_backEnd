package com.costco.gb.logging;

import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Logs persisted business changes only after their transaction commits. */
public final class BusinessLog {
    private BusinessLog() {
    }

    public static void info(Logger logger, String message, Object... arguments) {
        Object[] snapshot = arguments.clone();
        afterCommit(() -> logger.info(message, snapshot));
    }

    public static void debug(Logger logger, String message, Object... arguments) {
        if (logger.isDebugEnabled()) {
            Object[] snapshot = arguments.clone();
            afterCommit(() -> logger.debug(message, snapshot));
        }
    }

    public static void warn(Logger logger, String message, Object... arguments) {
        Object[] snapshot = arguments.clone();
        afterCommit(() -> logger.warn(message, snapshot));
    }

    /** Filesystem changes are not rolled back with the database. Flag them for reconciliation. */
    public static void warnOnRollback(Logger logger, String message, Object... arguments) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            Object[] snapshot = arguments.clone();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        logger.warn(message, snapshot);
                    }
                }
            });
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
