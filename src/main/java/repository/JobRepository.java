package repository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import model.Job;
import model.JobItem;
import model.JobSummary;
import utils.DBConnection;

public class JobRepository {

    /*
     * =====================================================
     * INSERT DRAFT JOB
     * =====================================================
     */

    public Job insertDraftJob(Connection con, String jobNo) throws SQLException {

        String sql = """
                    INSERT INTO jobs (job_no, status)
                    VALUES (?, 'DRAFT')
                """;

        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, jobNo);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (!rs.next()) {
                throw new SQLException("Failed to create draft job");
            }

            Job job = new Job();
            job.setId(rs.getInt(1));
            job.setJobNo(jobNo);
            job.setStatus("DRAFT");

            return job;
        }
    }

    /*
     * =====================================================
     * FIND LATEST DRAFT JOB (FOR RESUME)
     * =====================================================
     */

    public Job findLatestDraftJob() {

        String sql = """
                    SELECT *
                    FROM jobs
                    WHERE status = 'DRAFT'
                    ORDER BY created_at DESC
                    LIMIT 1
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return mapRowToJob(rs);
            }
            return null;

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch latest draft job", e);
        }
    }

    /*
     * =====================================================
     * FIND JOB BY ID
     * =====================================================
     */

    public Job findJobById(int jobId) {

        String sql = """
                    SELECT id, job_no, client_id, job_title, job_date,
                           status, remarks, created_at, updated_at
                    FROM jobs
                    WHERE id = ?
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapRowToJob(rs);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch job by id=" + jobId, e);
        }

        return null;
    }

    /*
     * =====================================================
     * FIND ALL JOBS
     * =====================================================
     */

    public List<Job> findAllJobs() {

        List<Job> list = new ArrayList<>();

        String sql = """
                    SELECT id, job_no, client_id, job_title, job_date,
                           status, remarks, created_at, updated_at
                    FROM jobs
                    ORDER BY DATE(job_date) DESC, id DESC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToJob(rs));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch all jobs", e);
        }

        return list;
    }

    /*
     * =====================================================
     * SEARCH JOBS
     * =====================================================
     */

    public List<Job> searchJobs(String keyword) {

        List<Job> list = new ArrayList<>();

        String sql = """
                    SELECT id, job_no, client_id, job_title, job_date,
                           status, remarks, created_at, updated_at
                    FROM jobs
                    WHERE LOWER(job_no) LIKE ?
                       OR LOWER(job_title) LIKE ?
                       OR LOWER(remarks) LIKE ?
                    ORDER BY DATE(job_date) DESC, id DESC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            String like = "%" + keyword.toLowerCase() + "%";

            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapRowToJob(rs));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to search jobs", e);
        }

        return list;
    }

    /*
     * =====================================================
     * JOB ITEMS
     * =====================================================
     */

    public List<JobItem> findJobItemsByJobId(int jobId) {

        List<JobItem> list = new ArrayList<>();

        String sql = """
                    SELECT id, job_id, type, description, amount, sort_order
                    FROM job_items
                    WHERE job_id = ?
                    ORDER BY sort_order ASC, id ASC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                JobItem item = new JobItem();
                item.setId(rs.getInt("id"));
                item.setJobId(rs.getInt("job_id"));
                item.setType(rs.getString("type"));
                item.setDescription(rs.getString("description"));
                item.setAmount(rs.getDouble("amount"));
                item.setSortOrder(rs.getInt("sort_order"));
                list.add(item);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load job items", e);
        }

        return list;
    }

    /*
     * =====================================================
     * COMMON ROW MAPPER (IMPORTANT)
     * =====================================================
     */

    private Job mapRowToJob(ResultSet rs) throws SQLException {

        Job job = new Job();

        job.setId(rs.getInt("id"));
        job.setJobNo(rs.getString("job_no"));
        job.setJobTitle(rs.getString("job_title"));

        int cid = rs.getInt("client_id");
        job.setClientId(rs.wasNull() ? null : cid);

        String dateStr = rs.getString("job_date");
        job.setJobDate(dateStr == null ? null : LocalDate.parse(dateStr));

        job.setStatus(rs.getString("status"));
        job.setRemarks(rs.getString("remarks"));

        // ✅ Keep created_at as String (your current model)
        job.setCreatedAt(rs.getString("created_at"));
        job.setUpdatedAt(rs.getString("updated_at"));

        return job;
    }

    public List<Job> findFullJobsByClientId(int clientId) {

        List<Job> list = new ArrayList<>();

        String sql = """
                    SELECT id, job_no, client_id, job_title, job_date,
                           status, remarks, created_at, updated_at
                    FROM jobs
                    WHERE client_id = ?
                    ORDER BY id DESC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, clientId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                Job job = new Job();
                job.setId(rs.getInt("id"));
                job.setJobNo(rs.getString("job_no"));

                Integer cid = rs.getObject("client_id") != null
                        ? rs.getInt("client_id")
                        : null;
                job.setClientId(cid);

                job.setJobTitle(rs.getString("job_title"));

                String jobDate = rs.getString("job_date");
                job.setJobDate(jobDate != null ? LocalDate.parse(jobDate) : null);

                job.setStatus(rs.getString("status"));
                job.setRemarks(rs.getString("remarks"));
                job.setCreatedAt(rs.getString("created_at"));
                job.setUpdatedAt(rs.getString("updated_at"));

                list.add(job);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch full jobs for clientId=" + clientId, e);
        }

        return list;
    }

    public List<JobSummary> findJobsByClientId(int clientId) {

        List<JobSummary> list = new ArrayList<>();

        String sql = """
                    SELECT id, job_no, job_title, job_date
                    FROM jobs
                    WHERE client_id = ? AND status IN ('Created', 'In Progress', 'Completed')
                    ORDER BY id DESC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, clientId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                int id = rs.getInt("id");
                String jobNo = rs.getString("job_no");
                String title = rs.getString("job_title");

                String dateStr = rs.getString("job_date");
                LocalDate jobDate = dateStr != null ? LocalDate.parse(dateStr) : null;

                list.add(new JobSummary(id, jobNo, title, jobDate));
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch job summaries for clientId=" + clientId, e);
        }

        return list;
    }

    /*
     * =====================================================
     * DASHBOARD AGGREGATIONS
     * =====================================================
     */

    public List<model.DashboardJobDTO> getRecentDashboardJobs(int limit) {
        List<model.DashboardJobDTO> list = new ArrayList<>();
        String sql = """
                    SELECT
                        j.job_no,
                        c.name as client_name,
                        j.job_title,
                        j.job_date,
                        j.status as workflow,
                        (SELECT SUM(amount) FROM job_items ji WHERE ji.job_id = j.id) as total_val
                    FROM jobs j
                    LEFT JOIN clients c ON j.client_id = c.id
                    WHERE j.status != 'DRAFT'
                    ORDER BY j.id DESC
                    LIMIT ?
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String jobNo = rs.getString("job_no");
                String cName = rs.getString("client_name");
                String orderClient = jobNo + (cName != null ? " / " + cName : "");

                String projectDetails = rs.getString("job_title");
                if (projectDetails == null)
                    projectDetails = "";

                String received = rs.getString("job_date");
                if (received == null)
                    received = "-";

                String dueDate = "-"; // Could be mapped if a due_date column exists later

                double val = rs.getDouble("total_val");
                String valuation = val > 0 ? String.format("₹%,.2f", val) : "-";

                String workflow = rs.getString("workflow");
                if (workflow == null)
                    workflow = "PENDING";

                list.add(
                        new model.DashboardJobDTO(orderClient, projectDetails, received, dueDate, valuation, workflow));
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch recent dashboard jobs: " + e.getMessage());
        }

        return list;
    }

    public java.util.Map<String, Integer> getJobDistributionCounts() {
        java.util.Map<String, Integer> distribution = new java.util.LinkedHashMap<>();
        String sql = """
                    SELECT status, COUNT(id) as status_count
                    FROM jobs
                    WHERE status != 'DRAFT' AND status IS NOT NULL
                    GROUP BY status
                    ORDER BY status_count DESC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                distribution.put(rs.getString("status"), rs.getInt("status_count"));
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch job distribution counts: " + e.getMessage());
        }
        return distribution;
    }

    /*
     * =====================================================
     * FIND JOBS BY INVOICE
     * =====================================================
     */
    public List<Job> findJobsByInvoice(model.InvoiceMaster inv) {

        List<Job> list = new ArrayList<>();

        if (inv == null || inv.getPeriodFrom() == null || inv.getPeriodTo() == null) {
            return list;
        }

        String sql = """
                    SELECT id, job_no, client_id, job_title, job_date,
                           status, remarks, created_at, updated_at
                    FROM jobs
                    WHERE client_id = ?
                      AND DATE(job_date) BETWEEN ? AND ?
                    ORDER BY DATE(job_date) ASC, id ASC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, inv.getClientId());
            ps.setString(2, inv.getPeriodFrom().toString());
            ps.setString(3, inv.getPeriodTo().toString());
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowToJob(rs));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch jobs for invoice=" + inv.getInvoiceNo(), e);
        }

        return list;
    }

}
