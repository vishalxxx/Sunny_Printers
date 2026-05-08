import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TestDB {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        Connection con = DriverManager.getConnection("jdbc:sqlite:database/sunnyprinters.db");
        String sql = "SELECT j.id, COALESCE(SUM(ji.amount), 0) FROM jobs j LEFT JOIN job_items ji ON j.id = ji.job_id GROUP BY j.id ORDER BY j.job_date DESC, j.id DESC";
        PreparedStatement ps = con.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        while(rs.next()) count++;
        System.out.println("Query took: " + (System.currentTimeMillis() - start) + "ms for " + count + " rows.");
    }
}
