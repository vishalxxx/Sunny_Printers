package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class WriteCoordinatedConnection implements InvocationHandler {
    private final Connection delegate;
    private boolean lockAcquired = false;
    private boolean lockAcquiredAsBackground = false;

    public WriteCoordinatedConnection(Connection delegate, boolean acquireLockImmediately) {
        this.delegate = delegate;
        if (acquireLockImmediately) {
            this.lockAcquiredAsBackground = SQLiteWriteCoordinator.isBackground();
            SQLiteWriteCoordinator.beginWrite();
            this.lockAcquired = true;
        }
    }

    public static Connection wrap(Connection conn, boolean acquireLockImmediately) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new WriteCoordinatedConnection(conn, acquireLockImmediately)
        );
    }

    public void forceAcquireLock() {
        if (!lockAcquired) {
            this.lockAcquiredAsBackground = SQLiteWriteCoordinator.isBackground();
            SQLiteWriteCoordinator.beginWrite();
            lockAcquired = true;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if ("setAutoCommit".equals(methodName) && args.length == 1) {
            boolean autoCommit = (Boolean) args[0];
            if (!autoCommit && !lockAcquired) {
                this.lockAcquiredAsBackground = SQLiteWriteCoordinator.isBackground();
                SQLiteWriteCoordinator.beginWrite();
                lockAcquired = true;
            }
        } else if ("close".equals(methodName)) {
            try {
                return method.invoke(delegate, args);
            } finally {
                if (lockAcquired) {
                    SQLiteWriteCoordinator.endWrite(lockAcquiredAsBackground);
                    lockAcquired = false;
                }
            }
        } else if (("prepareStatement".equals(methodName) || "prepareCall".equals(methodName)) && !lockAcquired) {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String sql = ((String) args[0]).trim().toUpperCase();
                if (isWriteQuery(sql)) {
                    this.lockAcquiredAsBackground = SQLiteWriteCoordinator.isBackground();
                    SQLiteWriteCoordinator.beginWrite();
                    lockAcquired = true;
                }
            }
        }

        try {
            Object result = method.invoke(delegate, args);
            if (result instanceof PreparedStatement) {
                return Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[]{PreparedStatement.class},
                        new StatementProxy((Statement) result, this)
                );
            } else if (result instanceof Statement) {
                return Proxy.newProxyInstance(
                        Statement.class.getClassLoader(),
                        new Class<?>[]{Statement.class},
                        new StatementProxy((Statement) result, this)
                );
            }
            return result;
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static boolean isWriteQuery(String sql) {
        return sql.startsWith("INSERT") || sql.startsWith("UPDATE") || sql.startsWith("DELETE")
                || sql.startsWith("REPLACE") || sql.startsWith("CREATE") || sql.startsWith("DROP")
                || sql.startsWith("ALTER") || sql.startsWith("PRAGMA") && (sql.contains("JOURNAL_MODE") || sql.contains("SYNCHRONOUS"));
    }

    private static class StatementProxy implements InvocationHandler {
        private final Statement delegate;
        private final WriteCoordinatedConnection connProxy;

        public StatementProxy(Statement delegate, WriteCoordinatedConnection connProxy) {
            this.delegate = delegate;
            this.connProxy = connProxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            if (name.startsWith("execute") || name.startsWith("addBatch")) {
                boolean isWrite = false;
                if (name.contains("Update") || name.contains("Batch")) {
                    isWrite = true;
                } else if (args != null && args.length > 0 && args[0] instanceof String) {
                    String sql = ((String) args[0]).trim().toUpperCase();
                    isWrite = isWriteQuery(sql);
                } else {
                    isWrite = true;
                }

                if (isWrite) {
                    connProxy.forceAcquireLock();
                }
            }

            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
