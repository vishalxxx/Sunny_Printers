package scratch;
import java.sql.*;

public class SchemaChecker {
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:database/sunnyprinters.db")) {
            System.out.println("Printing schema for invoice_master:");
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='invoice_master'")) {
                if (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }
}
