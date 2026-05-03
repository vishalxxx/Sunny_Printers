import service.JobService;

public class TestJobNo {
    public static void main(String[] args) {
        JobService js = new JobService();
        var job = js.createDraftJob();
        System.out.println(job.getJobNo());
    }
}
