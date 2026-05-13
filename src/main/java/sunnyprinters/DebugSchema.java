package sunnyprinters;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DebugSchema {
    public static void main(String[] args) {
        try (PrintWriter out = new PrintWriter("schema_dump.txt")) {
			utils.DBConnection.ensureDatabaseParentDirectory();
			try (Connection conn = DriverManager.getConnection("jdbc:sqlite:database/sunnyprinters.db");
                Statement stmt = conn.createStatement()) {

            out.println("=== TABLES ===");
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (rs.next()) {
                    out.println(rs.getString("name"));
                }
            }

            out.println("\n=== TRIGGERS ===");
            try (ResultSet rs = stmt
                    .executeQuery("SELECT name, tbl_name, sql FROM sqlite_master WHERE type='trigger'")) {
                while (rs.next()) {
                    out.println("Trigger: " + rs.getString("name"));
                    out.println("  Table: " + rs.getString("tbl_name"));
                    out.println("  SQL: " + rs.getString("sql"));
                    out.println("--------------------------------------------------");
                }
            }

			}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
