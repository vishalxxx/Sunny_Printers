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
import service.NumberSequenceAllocationService.AllocatedNumber;
import service.sync.UniversalNumberAllocator;
import utils.DBConnection;
import utils.DocumentNumbering;
import utils.JobIdentifiers;

public class JobRepository {

    private static final String JOB_SELECT = """
            j.uuid, j.job_code, j.client_uuid, j.job_title, j.job_date, j.job_type, j.description,
            j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path,
            CASE
                WHEN j.invoice_uuid IS NOT NULL AND EXISTS (
                    SELECT 1 FROM invoice_job_mapping m
                    WHERE m.job_uuid = j.uuid AND m.invoice_uuid = j.invoice_uuid
                      AND COALESCE(m.is_deleted, 0) = 0
                ) THEN j.invoice_uuid
                ELSE (SELECT m.invoice_uuid FROM invoice_job_mapping m
                      WHERE m.job_uuid = j.uuid AND COALESCE(m.is_deleted, 0) = 0 LIMIT 1)
            END AS invoice_uuid,
            j.amount, j.job_number_mode, j.delivery_date, j.is_deleted, j.is_active, j.sync_status, j.sync_version,
            j.created_by_user_uuid, j.updated_by_user_uuid,
            (SELECT inv.invoice_no FROM invoice_master inv WHERE inv.uuid = (
                CASE
                    WHEN j.invoice_uuid IS NOT NULL AND EXISTS (
                        SELECT 1 FROM invoice_job_mapping m
                        WHERE m.job_uuid = j.uuid AND m.invoice_uuid = j.invoice_uuid
                          AND COALESCE(m.is_deleted, 0) = 0
                    ) THEN j.invoice_uuid
                    ELSE (SELECT m.invoice_uuid FROM invoice_job_mapping m
                          WHERE m.job_uuid = j.uuid AND COALESCE(m.is_deleted, 0) = 0 LIMIT 1)
                END
            )) AS invoice_no,
            (SELECT inv.status FROM invoice_master inv WHERE inv.uuid = (
                CASE
                    WHEN j.invoice_uuid IS NOT NULL AND EXISTS (
                        SELECT 1 FROM invoice_job_mapping m
                        WHERE m.job_uuid = j.uuid AND m.invoice_uuid = j.invoice_uuid
                          AND COALESCE(m.is_deleted, 0) = 0
                    ) THEN j.invoice_uuid
                    ELSE (SELECT m.invoice_uuid FROM invoice_job_mapping m
                          WHERE m.job_uuid = j.uuid AND COALESCE(m.is_deleted, 0) = 0 LIMIT 1)
                END
            )) AS invoice_status,
            (SELECT inv.document_series FROM invoice_master inv WHERE inv.uuid = (
                CASE
                    WHEN j.invoice_uuid IS NOT NULL AND EXISTS (
                        SELECT 1 FROM invoice_job_mapping m
                        WHERE m.job_uuid = j.uuid AND m.invoice_uuid = j.invoice_uuid
                          AND COALESCE(m.is_deleted, 0) = 0
                    ) THEN j.invoice_uuid
                    ELSE (SELECT m.invoice_uuid FROM invoice_job_mapping m
                          WHERE m.job_uuid = j.uuid AND COALESCE(m.is_deleted, 0) = 0 LIMIT 1)
                END
            )) AS invoice_type,
            (SELECT COALESCE(SUM(ji.amount), 0) FROM job_items ji
                WHERE ji.job_uuid = j.uuid AND COALESCE(ji.is_deleted, 0) = 0) AS job_total
            """;

    private static final String JOB_ITEMS_TOTAL_SUBQUERY =
            "(SELECT COALESCE(SUM(ji.amount), 0) FROM job_items ji"
                    + " WHERE ji.job_uuid = j.uuid AND COALESCE(ji.is_deleted, 0) = 0)";

    /*
     * =====================================================
     * INSERT DRAFT JOB
     * =====================================================
     */

    public Job insertDraftJob(Connection con) throws SQLException {
        String uuid = JobIdentifiers.newUuidString();
        String code;
        boolean tempCode;
        try {
            AllocatedNumber allocated = UniversalNumberAllocator.getInstance().allocateJobCode(con);
            code = allocated.value();
            tempCode = allocated.temporary();
        } catch (Exception e) {
            throw new SQLException("Failed to allocate job_code", e);
        }
        String syncStatus = tempCode || DocumentNumbering.isTemporaryNumber(code) ? "PENDING" : "PENDING";
        
        String userUuid = null;
        if (utils.SessionManager.getInstance().getCurrentUser() != null) {
            userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
        }

        String sql = """
                INSERT INTO jobs (uuid, client_uuid, job_code, status, sync_status, created_by_user_uuid, updated_by_user_uuid)
                VALUES (?, '', ?, 'DRAFT', ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, code);
            ps.setString(3, syncStatus);
            ps.setString(4, userUuid);
            ps.setString(5, userUuid);
            ps.executeUpdate();
        }
        Job job = new Job();
        job.setUuid(uuid);
        job.setJobCode(code);
        job.setStatus("DRAFT");
        job.setSyncStatus(syncStatus);
        job.setCreatedByUserUuid(userUuid);
        job.setUpdatedByUserUuid(userUuid);
        return job;
    }

    public Job insertJob(Connection con, Job job) throws SQLException {
        String uuid = job.hasUuid() ? job.getUuid() : JobIdentifiers.newUuidString();
        String code = job.getJobCode();
        if (code == null || code.isBlank()) {
            try {
                AllocatedNumber allocated = UniversalNumberAllocator.getInstance().allocateJobCode(con);
                code = allocated.value();
                if (allocated.temporary() || DocumentNumbering.isTemporaryNumber(code)) {
                    job.setSyncStatus("PENDING");
                }
            } catch (Exception e) {
                throw new SQLException("Failed to allocate job_code", e);
            }
        }
        String userUuid = null;
        if (utils.SessionManager.getInstance().getCurrentUser() != null) {
            userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
        }
        job.setCreatedByUserUuid(userUuid);
        job.setUpdatedByUserUuid(userUuid);

        String sql = """
                INSERT INTO jobs (
                  uuid, client_uuid, job_code, job_title, job_date, status, image_path, remarks, job_type, description,
                  created_by_user_uuid, updated_by_user_uuid
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, job.getClientUuid() != null ? job.getClientUuid() : "");
            ps.setString(3, code);
            ps.setString(4, job.getJobTitle());
            if (job.getJobDate() != null) {
                ps.setString(5, job.getJobDate().toString());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setString(6, job.getStatus());
            ps.setString(7, job.getImagePath());
            ps.setString(8, job.getRemarks());
            ps.setString(9, job.getJobType());
            ps.setString(10, job.getDescription());
            ps.setString(11, userUuid);
            ps.setString(12, userUuid);
            ps.executeUpdate();
        }
        job.setUuid(uuid);
        job.setJobCode(code);
        return job;
    }

    /*
     * =====================================================
     * FIND LATEST DRAFT JOB (FOR RESUME)
     * =====================================================
     */

    public Job findLatestDraftJob() {

        String sql = "SELECT " + JOB_SELECT + " FROM jobs j WHERE UPPER(TRIM(j.status)) = 'DRAFT' ORDER BY j.created_at DESC LIMIT 1";

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

    public Job findJobByUuid(String jobUuid) {
        if (jobUuid == null || jobUuid.isBlank()) {
            return null;
        }
        String sql = "SELECT " + JOB_SELECT + " FROM jobs j WHERE j.uuid = ?";
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobUuid.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToJob(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch job uuid=" + jobUuid, e);
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

        String sql = "SELECT " + JOB_SELECT + " FROM jobs j WHERE (j.job_type IS NULL OR j.job_type != 'CHARGE') ORDER BY COALESCE(j.updated_at, j.created_at) DESC, j.created_at DESC";

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

        String sql = "SELECT " + JOB_SELECT + """
                 FROM jobs j
                 WHERE (LOWER(j.job_code) LIKE ?
                    OR LOWER(j.job_title) LIKE ?
                    OR LOWER(j.remarks) LIKE ?)
                   AND (j.job_type IS NULL OR j.job_type != 'CHARGE')
                 ORDER BY COALESCE(j.updated_at, j.created_at) DESC, j.created_at DESC
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

    public List<JobItem> findJobItemsByJobUuid(String jobUuid) {

        List<JobItem> list = new ArrayList<>();

        String sql = """
                    SELECT uuid, job_uuid, type, description, amount, sort_order
                    FROM job_items
                    WHERE job_uuid = ? AND is_deleted = 0
                    ORDER BY sort_order ASC, uuid ASC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, jobUuid);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                JobItem item = new JobItem();
                item.setUuid(rs.getString("uuid"));
                item.setJobUuid(rs.getString("job_uuid"));
                item.setType(rs.getString("type"));
                item.setDescription(rs.getString("description"));
                item.setAmount(rs.getDouble("amount"));
                item.setSortOrder(rs.getInt("sort_order"));
                list.add(item);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load job items for jobUuid=" + jobUuid, e);
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

        job.setUuid(rs.getString("uuid"));
        job.setJobCode(rs.getString("job_code"));
        job.setJobTitle(rs.getString("job_title"));
        job.setJobType(rs.getString("job_type"));
        job.setDescription(rs.getString("description"));

        String cid = rs.getString("client_uuid");
        job.setClientUuid(cid == null || cid.isBlank() ? null : cid.trim());

        job.setJobDate(parseJobDateFlexible(rs.getString("job_date")));
        job.setDeliveryDate(parseJobDateFlexible(rs.getString("delivery_date")));

        job.setStatus(rs.getString("status"));
        try {
            job.setChildStatus(rs.getString("child_status"));
        } catch (SQLException ignore) {}
        job.setRemarks(rs.getString("remarks"));
        try {
            job.setJobNumberMode(rs.getString("job_number_mode"));
        } catch (SQLException ignore) {}
        try {
            job.setAmount(rs.getDouble("amount"));
        } catch (SQLException ignore) {}

        job.setCreatedAt(rs.getString("created_at"));
        job.setUpdatedAt(rs.getString("updated_at"));
        try {
            job.setSyncStatus(rs.getString("sync_status"));
            job.setSyncVersion(rs.getInt("sync_version"));
            job.setIsDeleted(rs.getInt("is_deleted"));
            job.setIsActive(rs.getInt("is_active"));
        } catch (SQLException ignore) {}
        try {
            job.setCreatedByUserUuid(rs.getString("created_by_user_uuid"));
            job.setUpdatedByUserUuid(rs.getString("updated_by_user_uuid"));
        } catch (SQLException ignore) {}

        String invUuid = rs.getString("invoice_uuid");
        job.setInvoiceUuid(invUuid == null || invUuid.isBlank() ? null : invUuid.trim());

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

        try {
            job.setInvoiceType(rs.getString("invoice_type"));
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

    public List<Job> findFullJobsByClientId(String clientId) {

        List<Job> list = new ArrayList<>();

        String sql = "SELECT " + JOB_SELECT + " FROM jobs j WHERE j.client_uuid = ? AND (j.job_type IS NULL OR j.job_type != 'CHARGE') ORDER BY j.created_at DESC";

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapRowToJob(rs));
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch full jobs for clientUuid=" + clientId, e);
        }

        return list;
    }

    public List<JobSummary> findJobsByClientId(String clientId) {

        List<JobSummary> list = new ArrayList<>();

        String sql = """
                    SELECT uuid, job_code, job_title, job_date
                    FROM jobs
                    WHERE client_uuid = ?
                      AND invoice_uuid IS NULL
                      AND (job_type IS NULL OR job_type != 'CHARGE')
                      AND LOWER(TRIM(REPLACE(COALESCE(status,''), '_', ' '))) = 'completed'
                    ORDER BY created_at DESC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                String uuid = rs.getString("uuid");
                String jobCode = rs.getString("job_code");
                String title = rs.getString("job_title");
                LocalDate jobDate = parseJobDateFlexible(rs.getString("job_date"));
                list.add(new JobSummary(uuid, jobCode, title, jobDate));
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch job summaries for clientId=" + clientId, e);
        }

        return list;
    }

    public List<Job> findCompletedJobsByClientId(String clientId) {
        List<Job> list = new ArrayList<>();
        String sql = "SELECT " + JOB_SELECT + " FROM jobs j "
                + "WHERE j.client_uuid = ? AND j.invoice_uuid IS NULL "
                + "AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed' "
                + "ORDER BY j.created_at DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToJob(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch completed jobs for clientUuid=" + clientId, e);
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
                        j.job_code,
                        COALESCE(c.business_name, c.client_name) as client_name,
                        j.job_title,
                        j.job_date,
                        j.status as workflow,
                        (SELECT SUM(amount) FROM job_items ji WHERE ji.job_uuid = j.uuid) as total_val
                    FROM jobs j
                    LEFT JOIN clients c ON j.client_uuid = c.uuid
                    WHERE j.status != 'DRAFT' AND (j.job_type IS NULL OR j.job_type != 'CHARGE')
                    ORDER BY j.created_at DESC
                    LIMIT ?
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String jobNo = rs.getString("job_code");
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
                    SELECT status, COUNT(uuid) as status_count
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

        if (inv == null || inv.getUuid() == null) {
            return list;
        }

        String sql = "SELECT " + JOB_SELECT + """
                 FROM jobs j
                 WHERE j.invoice_uuid = ?
                    OR j.uuid IN (
                      SELECT m.job_uuid FROM invoice_job_mapping m
                      WHERE m.invoice_uuid = ? AND COALESCE(m.is_deleted, 0) = 0
                    )
                 ORDER BY DATE(j.job_date) ASC, j.created_at ASC
                """;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, inv.getUuid());
            ps.setString(2, inv.getUuid());

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
    public List<Job> findCompletedJobsByClientIdInDateRange(String clientId, LocalDate from, LocalDate to) {
        List<Job> list = new ArrayList<>();
        String sql = "SELECT " + JOB_SELECT + """
                 FROM jobs j
                 WHERE j.client_uuid = ?
                   AND j.invoice_uuid IS NULL
                   AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
                   AND DATE(j.job_date) >= DATE(?)
                   AND DATE(j.job_date) <= DATE(?)
                 ORDER BY j.created_at DESC
                """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToJob(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch completed jobs in range for clientUuid=" + clientId, e);
        }
        return list;
    }

    /**
     * Completed, un-invoiced jobs in [from, to] for all clients (job_date in range).
     */
    public List<Job> findCompletedJobsAllClientsInDateRange(LocalDate from, LocalDate to) {
        List<Job> list = new ArrayList<>();
        String sql = "SELECT " + JOB_SELECT + ", c.business_name AS client_business_name "
                + "FROM jobs j "
                + "INNER JOIN clients c ON c.uuid = j.client_uuid "
                + "WHERE j.invoice_uuid IS NULL "
                + "AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed' "
                + "AND DATE(j.job_date) BETWEEN DATE(?) AND DATE(?) "
                + "ORDER BY c.business_name ASC, DATE(j.job_date) DESC, j.created_at DESC";
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
    public List<String> findCompletedJobUuidsByClientIdInDateRange(String clientId, LocalDate from, LocalDate to) {
        List<String> list = new ArrayList<>();
        String sql = """
                    SELECT j.uuid
                    FROM jobs j
                    WHERE j.client_uuid = ?
                      AND j.invoice_uuid IS NULL
                      AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
                      AND DATE(j.job_date) BETWEEN DATE(?) AND DATE(?)
                    ORDER BY j.created_at DESC
                """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("uuid"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch completed job uuids in range for clientId=" + clientId, e);
        }
        return list;
    }

    /**
     * Sets {@code jobs.amount} to the sum of active {@code job_items} for the job.
     * Marks the job pending sync when amount changes.
     */
    public static void syncAmountFromJobItems(Connection con, String jobUuid) {
        if (con == null || jobUuid == null || jobUuid.isBlank()) {
            return;
        }
        String sql = """
                UPDATE jobs SET
                  amount = (
                    SELECT COALESCE(SUM(ji.amount), 0)
                    FROM job_items ji
                    WHERE ji.job_uuid = ? AND COALESCE(ji.is_deleted, 0) = 0
                  ),
                  sync_status = CASE
                    WHEN COALESCE(sync_status, '') = 'SYNCED' THEN 'PENDING'
                    ELSE COALESCE(NULLIF(TRIM(sync_status), ''), 'PENDING')
                  END,
                  sync_version = COALESCE(sync_version, 0) + 1,
                  updated_at = datetime('now')
                WHERE uuid = ?
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            String id = jobUuid.trim();
            ps.setString(1, id);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync job amount from items for uuid=" + jobUuid, e);
        }
    }

    /** Sum of job_items.amount for the given jobs (invoice-style line totals). */
    public double sumJobItemsAmountForJobUuids(List<String> jobUuids) {
        if (jobUuids == null || jobUuids.isEmpty()) {
            return 0.0;
        }
        String placeholders = String.join(",", jobUuids.stream().map(x -> "?").toList());
        String sql = "SELECT COALESCE(SUM(amount), 0) AS total_amt FROM job_items WHERE job_uuid IN ("
                + placeholders + ")";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (String uuid : jobUuids) {
                ps.setString(i++, uuid);
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

    /** Sum printing quantities for the given job uuids (via job_items → printing_items). */
    public long sumPrintingQtyForJobUuids(List<String> jobUuids) {
        if (jobUuids == null || jobUuids.isEmpty()) {
            return 0L;
        }
        String placeholders = String.join(",", jobUuids.stream().map(x -> "?").toList());
        String sql = """
                    SELECT COALESCE(SUM(pi.qty), 0) AS total_qty
                    FROM printing_items pi
                    INNER JOIN job_items ji ON pi.job_item_uuid = ji.uuid
                    WHERE ji.job_uuid IN (""" + placeholders + ") AND ji.is_deleted = 0 AND pi.is_deleted = 0";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (String uuid : jobUuids) {
                ps.setString(i++, uuid);
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

    public void updateChildStatus(Connection con, String jobUuid, String childStatus) throws SQLException {
        String userUuid = null;
        if (utils.SessionManager.getInstance().getCurrentUser() != null) {
            userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
        }
        String sql = """
                UPDATE jobs SET child_status = ?, sync_status = 'PENDING',
                updated_at = datetime('now'), sync_version = sync_version + 1,
                updated_by_user_uuid = ?
                WHERE uuid = ?
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (childStatus == null || childStatus.isBlank()) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, childStatus);
            }
            ps.setString(2, userUuid);
            ps.setString(3, jobUuid);
            ps.executeUpdate();
        }
    }

    public List<Job> findPendingForSync() {
        List<Job> list = new ArrayList<>();
        String sql = """
                SELECT j.uuid, j.job_code, j.client_uuid, j.job_title, j.job_date, j.job_type, j.description,
                       j.status, j.child_status, j.remarks, j.created_at, j.updated_at, j.image_path, j.invoice_uuid,
                       j.amount, j.job_number_mode, j.delivery_date, j.is_deleted, j.is_active, j.sync_status, j.sync_version,
                       j.created_by_user_uuid, j.updated_by_user_uuid,
                       NULL AS invoice_no, NULL AS invoice_status, """
                + JOB_ITEMS_TOTAL_SUBQUERY + " AS job_total\n"
                + """
                FROM jobs j
                WHERE UPPER(TRIM(COALESCE(j.sync_status, ''))) IN ('', 'PENDING', 'WAITING_DEPENDENCY')
                  AND j.job_code NOT LIKE 'TEMP-%'
                ORDER BY j.created_at ASC
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToJob(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

}
