public class TestDb {
    public static void main(String[] args) throws Exception {
        utils.DBConnection.getExclusiveConnection().close();
        System.out.println("Migration Triggered successfully!");
    }
}
