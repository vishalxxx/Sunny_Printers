package utils;

import java.sql.Connection;

public class AtomicDB {

    // =========================
    // FUNCTION WITH EXCEPTION
    // =========================
    @FunctionalInterface
    public interface SQLFunction<T> {
        T apply(Connection con) throws Exception;
    }

    // =========================
    // CONSUMER WITH EXCEPTION
    // =========================
    @FunctionalInterface
    public interface SQLConsumer {
        void accept(Connection con) throws Exception;
    }

    // =========================
    // RETURNING TRANSACTION
    // =========================
    public static <T> T run(SQLFunction<T> action) {

        try (Connection con = DBConnection.getConnection()) {

            try {
                con.setAutoCommit(false);

                T result = action.apply(con);

                con.commit();
                return result;

            } catch (Exception e) {
                con.rollback();
                throw new RuntimeException("Transaction rolled back", e);
            }

        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new RuntimeException("Database transaction failed: " + (msg != null ? msg : e.getClass().getSimpleName()), e);
        }
    }

    // =========================
    // EXCLUSIVE TRANSACTION (FOR HEAVY WRITES WITH NETWORK)
    // =========================
    public static <T> T runExclusive(SQLFunction<T> action) {
        try (Connection con = DBConnection.getExclusiveConnection()) {
            try {
                con.setAutoCommit(false);
                T result = action.apply(con);
                con.commit();
                return result;
            } catch (Exception e) {
                con.rollback();
                throw new RuntimeException("Transaction rolled back", e);
            }
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new RuntimeException("Database transaction failed: " + (msg != null ? msg : e.getClass().getSimpleName()), e);
        }
    }

    // =========================
    // EXCLUSIVE VOID TRANSACTION
    // =========================
    public static void runExclusiveVoid(SQLConsumer action) {
        try (Connection con = DBConnection.getExclusiveConnection()) {
            try {
                con.setAutoCommit(false);
                action.accept(con);
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw new RuntimeException("Transaction rolled back", e);
            }
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new RuntimeException("Database transaction failed: " + (msg != null ? msg : e.getClass().getSimpleName()), e);
        }
    }

    // =========================
    // VOID TRANSACTION
    // =========================
    public static void runVoid(SQLConsumer action) {

        try (Connection con = DBConnection.getConnection()) {

            try {
                con.setAutoCommit(false);

                action.accept(con);

                con.commit();

            } catch (Exception e) {
                con.rollback();
                throw new RuntimeException("Transaction rolled back", e);
            }

        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new RuntimeException("Database transaction failed: " + (msg != null ? msg : e.getClass().getSimpleName()), e);
        }
    }
}
