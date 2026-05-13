package repository;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import model.Job;
import model.JobItem;
import model.JobSummary;
import utils.DBConnection;

public class JobRepository {

    private static String tempJobRowKey() {
        return "TMP" + System.nanoTime();
    }

    private static String canonicalJobNo(int id) {
        return "JOB-" + id;
    }

    /*
     * =====================================================
     * INSERT DRAFT JOB
     * =====================================================
     */

    public Job insertDraftJob(Connection con) throws SQLException {

        String sql = """
                    INSERT INTO jobs (job_no, status)
                    VALUES (?, 'DRAFT')
                """;

        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, tempJobRowKey());
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (!rs.next()) {
                throw new SQLException("Failed to create draft job");
            }

            int id = rs.getInt(1);
            String jobNo = canonicalJobNo(id);
            try (PreparedStatement up = con.prepareStatement("UPDATE jobs SET job_no = ? WHERE id = ?")) {
                up.setString(1, jobNo);
                up.setInt(2, id);
                up.executeUpdate();
            }

            Job job = new Job();
            job.setId(id);
            job.setJobNo(jobNo);
            job.setStatus("DRAFT");

            return job;
        }
    }

    public Job insertJob(Connection con, Job job) throws SQLException {
        String sql = """
                    INSERT INTO jobs (job_no, client_id, job_title, job_date, status, image_path, remarks)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tempJobRowKey());
            ps.setObject(2, job.getClientId());
            ps.setString(3, job.getJobTitle());
            if (job.getJobDate() != null) {
                ps.setString(4, job.getJobDate().toString());
            } else {
                ps.setNull(4, java.sql.Types.DATE);
            }
            ps.setString(5, job.getStatus());
            ps.setString(6, job.getImagePath());
            ps.setString(7, job.getRemarks());
            
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (!rs.next()) {
                throw new SQLException("Failed to insert job");
            }
            int id = rs.getInt(1);
            String jobNo = canonicalJobNo(id);
            try (PreparedStatement up = con.prepareStatement("UPDATE jobs SET job_no = ? WHERE id = ?")) {
                up.setString(1, jobNo);
                up.setInt(2, id);
                up.executeUpdate();
            }
            job.setId(id);
            job.setJobNo(jobNo);
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
                    ORDER BY id DESC
                    LIMIT 1
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                Job job = mapRowToJob(rs);
                if ("DRAFT".equalsIgnoreCase(job.getStatus())) {
                    return job;
                }
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
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total
                    FROM jobs j
                    WHERE j.id = ?
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
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total
                    FROM jobs j
                    ORDER BY DATE(j.job_date) DESC, j.id DESC
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
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total
                    FROM jobs j
                    WHERE LOWER(j.job_no) LIKE ?
                       OR LOWER(j.job_title) LIKE ?
                       OR LOWER(j.remarks) LIKE ?
                    ORDER BY DATE(j.job_date) DESC, j.id DESC
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

        job.setJobDate(parseJobDateFlexible(rs.getString("job_date")));

        job.setStatus(rs.getString("status"));
        try {
            job.setChildStatus(rs.getString("child_status"));
        } catch (SQLException ignore) {}
        job.setRemarks(rs.getString("remarks"));

        // ✅ Keep created_at as String (your current model)
        job.setCreatedAt(rs.getString("created_at"));
        job.setUpdatedAt(rs.getString("updated_at"));
        
        try {
            int invId = rs.getInt("invoice_id");
            job.setInvoiceId(rs.wasNull() ? null : invId);
        } catch (SQLException ignore) {}

        try {
            double total = rs.getDouble("job_total");
            job.setJobTotal(rs.wasNull() ? null : total);
        } catch (SQLException ignore) {}

        try {
            job.setImagePath(rs.getString("image_path"));
        } catch (SQLException ignore) {}

        try {
            job.setInvoiceNo(rs.getString("invoice_no"));
        } catch (SQLException ignore) {}

        try {
            job.setInvoiceStatus(rs.getString("invoice_status"));
        } catch (SQLException ignore) {}

        return job;
    }

    /**
     * Parses job_date from SQLite (ISO date, datetime string, or dd/MM/yyyy). Used so aggregates
     * and lists do not fail the whole query when one row has a non-ISO format.
     */
    private static LocalDate parseJobDateFlexible(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        String s = dateStr.trim();
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e1) {
            if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                try {
                    return LocalDate.parse(s.substring(0, 10));
                } catch (DateTimeParseException ignored) {
                }
            }
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/uuuu"));
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/uuuu"));
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    public List<Job> findFullJobsByClientId(int clientId) {

        List<Job> list = new ArrayList<>();

        String sql = """
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total
                    FROM jobs j
                    WHERE j.client_id = ?
                    ORDER BY j.id DESC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, clientId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapRowToJob(rs));
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
                    WHERE client_id = ?
                      AND invoice_id IS NULL
                      AND LOWER(TRIM(REPLACE(COALESCE(status,''), '_', ' '))) = 'completed'
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

                LocalDate jobDate = parseJobDateFlexible(rs.getString("job_date"));

                list.add(new JobSummary(id, jobNo, title, jobDate));
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch job summaries for clientId=" + clientId, e);
        }

        return list;
    }

    public List<Job> findCompletedJobsByClientId(int clientId) {
        List<Job> list = new ArrayList<>();
        String sql = """
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total
                    FROM jobs j
                    WHERE j.client_id = ?
                      AND j.invoice_id IS NULL
                      AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
                    ORDER BY j.id DESC
                """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToJob(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch completed jobs for clientId=" + clientId, e);
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
                        COALESCE(c.business_name, c.client_name) as client_name,
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

        if (inv == null) {
            return list;
        }

        String sql = """
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master invM WHERE invM.id = m.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master invM WHERE invM.id = m.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total
                    FROM jobs j
                    JOIN invoice_job_mapping m ON j.id = m.job_id
                    WHERE m.invoice_id = ?
                    ORDER BY DATE(j.job_date) ASC, j.id ASC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, inv.getId());
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowToJob(rs));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch jobs for invoice=" + inv.getInvoiceNo(), e);
        }

        return list;
    }

    /**
     * Completed, un-invoiced jobs for a client whose job_date falls in [from, to] (inclusive).
     */
    public List<Job> findCompletedJobsByClientIdInDateRange(int clientId, LocalDate from, LocalDate to) {
        List<Job> list = new ArrayList<>();
        String sql = """
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total
                    FROM jobs j
                    WHERE j.client_id = ?
                      AND j.invoice_id IS NULL
                      AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
                      AND DATE(j.job_date) >= DATE(?)
                      AND DATE(j.job_date) <= DATE(?)
                    ORDER BY j.id DESC
                """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToJob(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch completed jobs in range for clientId=" + clientId, e);
        }
        return list;
    }

    /**
     * Completed, un-invoiced jobs in [from, to] for all clients (job_date in range).
     */
    public List<Job> findCompletedJobsAllClientsInDateRange(LocalDate from, LocalDate to) {
        List<Job> list = new ArrayList<>();
        String sql = """
                    SELECT j.id, j.job_no, j.client_id, j.job_title, j.job_date,
                           j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_id,
                           (SELECT invoice_no FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_no,
                           (SELECT status FROM invoice_master inv WHERE inv.id = j.invoice_id) as invoice_status,
                           (SELECT COALESCE(SUM(amount), 0) FROM job_items ji WHERE ji.job_id = j.id) as job_total,
                           c.business_name AS client_business_name
                    FROM jobs j
                    INNER JOIN clients c ON c.id = j.client_id
                    WHERE j.invoice_id IS NULL
                      AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
                      AND DATE(j.job_date) BETWEEN DATE(?) AND DATE(?)
                    ORDER BY c.business_name ASC, DATE(j.job_date) DESC, j.id DESC
                """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Job job = mapRowToJob(rs);
                    job.setClientBusinessName(rs.getString("client_business_name"));
                    list.add(job);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch completed jobs in range for all clients", e);
        }
        return list;
    }

    /**
     * IDs only (no row mapping) for completed, un-invoiced jobs in [from, to] by job_date.
     * Avoids date-text parsing in Java so non-ISO {@code job_date} values cannot zero out aggregates.
     */
    public List<Integer> findCompletedJobIdsByClientIdInDateRange(int clientId, LocalDate from, LocalDate to) {
        List<Integer> list = new ArrayList<>();
        String sql = """
                    SELECT j.id
                    FROM jobs j
                    WHERE j.client_id = ?
                      AND j.invoice_id IS NULL
                      AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
                      AND DATE(j.job_date) BETWEEN DATE(?) AND DATE(?)
                    ORDER BY j.id DESC
                """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt("id"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch completed job ids in range for clientId=" + clientId, e);
        }
        return list;
    }

    /** Sum of job_items.amount for the given jobs (invoice-style line totals). */
    public double sumJobItemsAmountForJobIds(List<Integer> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return 0.0;
        }
        String placeholders = String.join(",", jobIds.stream().map(x -> "?").toList());
        String sql = "SELECT COALESCE(SUM(amount), 0) AS total_amt FROM job_items WHERE job_id IN ("
                + placeholders + ")";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (Integer id : jobIds) {
                ps.setInt(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("total_amt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to sum job_items for jobs", e);
        }
        return 0.0;
    }

    /** Sum printing quantities for the given job ids (via job_items → printing_items). */
    public long sumPrintingQtyForJobIds(List<Integer> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return 0L;
        }
        String placeholders = String.join(",", jobIds.stream().map(x -> "?").toList());
        String sql = """
                    SELECT COALESCE(SUM(pi.qty), 0) AS total_qty
                    FROM printing_items pi
                    INNER JOIN job_items ji ON pi.job_item_id = ji.id
                    WHERE ji.job_id IN (""" + placeholders + ")";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (Integer id : jobIds) {
                ps.setInt(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("total_qty");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to sum printing qty for jobs", e);
        }
        return 0L;
    }

    public void updateChildStatus(Connection con, int jobId, String childStatus) throws SQLException {
        String sql = "UPDATE jobs SET child_status = ? WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (childStatus == null || childStatus.isBlank()) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, childStatus);
            }
            ps.setInt(2, jobId);
            ps.executeUpdate();
        }
    }

}
