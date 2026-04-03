import java.sql.Connection;
import utils.DBConnection;
import service.JobService;

public class TestJobNo {
    public static void main(String[] args) {
        try (Connection con = DBConnection.getConnection()) {
            System.out.println(JobService.JobNumberGenerator.generate(con));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
