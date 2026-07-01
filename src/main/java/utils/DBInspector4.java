package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBInspector4 {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:database/sunnyprinters.db?transaction_mode=IMMEDIATE";
        try (Connection con = DriverManager.getConnection(url)) {
            con.setAutoCommit(false);
            System.out.println("AutoCommit: " + con.getAutoCommit());
            try (Statement st = con.createStatement()) {
                st.execute("SELECT 1");
                System.out.println("Transaction started successfully with IMMEDIATE mode.");
            }
            con.rollback();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
