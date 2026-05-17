package repository;

import java.sql.*;

import model.SystemSettings;

public class SystemSettingsRepository {

	private static final String DEFAULT_UUID = "00000000-0000-0000-0000-000000000001";

	private static final String UPSERT = """
			INSERT INTO system_settings
			(uuid, invoice_mode, invoice_prefix, invoice_start_no,
			 invoice_padding, last_invoice_no, last_job_no,
			 job_prefix, job_start_no, job_padding, last_temp_invoice_no,
			 numbering_fy,
			 last_seq_inv, last_seq_pi, last_seq_cn, last_seq_dn,
			 last_seq_qtn, last_seq_po, last_seq_job, last_seq_tkt,
			 last_seq_dc, last_seq_ewb)
			VALUES (?, 'MANUAL', ?, ?, ?, ?, ?, ?, ?, ?, ?,
			        ?,
			        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(uuid) DO UPDATE SET
			    invoice_prefix = excluded.invoice_prefix,
			    invoice_start_no = excluded.invoice_start_no,
			    invoice_padding = excluded.invoice_padding,
			    last_invoice_no = excluded.last_invoice_no,
			    last_job_no = excluded.last_job_no,
			    job_prefix = excluded.job_prefix,
			    job_start_no = excluded.job_start_no,
			    job_padding = excluded.job_padding,
			    last_temp_invoice_no = excluded.last_temp_invoice_no,
			    numbering_fy = excluded.numbering_fy,
			    last_seq_inv = excluded.last_seq_inv,
			    last_seq_pi = excluded.last_seq_pi,
			    last_seq_cn = excluded.last_seq_cn,
			    last_seq_dn = excluded.last_seq_dn,
			    last_seq_qtn = excluded.last_seq_qtn,
			    last_seq_po = excluded.last_seq_po,
			    last_seq_job = excluded.last_seq_job,
			    last_seq_tkt = excluded.last_seq_tkt,
			    last_seq_dc = excluded.last_seq_dc,
			    last_seq_ewb = excluded.last_seq_ewb,
			    updated_at = CURRENT_TIMESTAMP
			""";

	private static final String SELECT = """
			SELECT invoice_mode,
			       invoice_prefix,
			       invoice_start_no,
			       invoice_padding,
			       last_invoice_no,
			       last_job_no,
			       job_prefix,
			       job_start_no,
			       job_padding,
			       last_temp_invoice_no,
			       numbering_fy,
			       last_seq_inv, last_seq_pi, last_seq_cn, last_seq_dn,
			       last_seq_qtn, last_seq_po, last_seq_job, last_seq_tkt,
			       last_seq_dc, last_seq_ewb
			FROM system_settings
			WHERE uuid = ?
			""";

	public SystemSettings load(Connection con) throws Exception {

		try (PreparedStatement ps = con.prepareStatement(SELECT)) {
			ps.setString(1, DEFAULT_UUID);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					SystemSettings s = new SystemSettings();
					s.setInvoiceMode(rs.getString("invoice_mode"));
					s.setInvoicePrefix(rs.getString("invoice_prefix"));
					s.setInvoiceStartNo(rs.getInt("invoice_start_no"));
					s.setInvoicePadding(rs.getInt("invoice_padding"));
					s.setLastInvoiceNo(rs.getInt("last_invoice_no"));
					s.setLastJobNo(rs.getInt("last_job_no"));

					s.setJobPrefix(rs.getString("job_prefix"));
					s.setJobStartNo(rs.getInt("job_start_no"));
					s.setJobPadding(rs.getInt("job_padding"));
					s.setLastTempInvoiceNo(rs.getInt("last_temp_invoice_no"));

					s.setNumberingFy(rs.getString("numbering_fy"));
					s.setLastSeqInv(rs.getInt("last_seq_inv"));
					s.setLastSeqPi(rs.getInt("last_seq_pi"));
					s.setLastSeqCn(rs.getInt("last_seq_cn"));
					s.setLastSeqDn(rs.getInt("last_seq_dn"));
					s.setLastSeqQtn(rs.getInt("last_seq_qtn"));
					s.setLastSeqPo(rs.getInt("last_seq_po"));
					s.setLastSeqJob(rs.getInt("last_seq_job"));
					s.setLastSeqTkt(rs.getInt("last_seq_tkt"));
					s.setLastSeqDc(rs.getInt("last_seq_dc"));
					s.setLastSeqEwb(rs.getInt("last_seq_ewb"));
					return s;
				}
			}
		}

		SystemSettings def = new SystemSettings();
		def.setInvoiceMode("AUTO");
		def.setInvoicePrefix("INV-");
		def.setInvoiceStartNo(1);
		def.setInvoicePadding(4);
		def.setLastInvoiceNo(0);
		def.setLastJobNo(0);
		def.setJobPrefix("SUN-");
		def.setJobStartNo(1);
		def.setJobPadding(4);
		def.setLastTempInvoiceNo(0);
		def.setNumberingFy("");
		def.resetAllSeriesForNewFinancialYear();
		return def;
	}

	public void save(Connection con, SystemSettings s) throws Exception {

		try (PreparedStatement ps = con.prepareStatement(UPSERT)) {
			ps.setString(1, DEFAULT_UUID);
			ps.setString(2, s.getInvoicePrefix());
			ps.setInt(3, s.getInvoiceStartNo());
			ps.setInt(4, s.getInvoicePadding());
			ps.setInt(5, s.getLastInvoiceNo());
			ps.setInt(6, s.getLastJobNo());

			ps.setString(7, s.getJobPrefix() == null ? "SUN-" : s.getJobPrefix());
			ps.setInt(8, s.getJobStartNo());
			ps.setInt(9, s.getJobPadding());
			ps.setInt(10, s.getLastTempInvoiceNo());

			ps.setString(11, s.getNumberingFy() != null ? s.getNumberingFy() : "");

			ps.setInt(12, s.getLastSeqInv());
			ps.setInt(13, s.getLastSeqPi());
			ps.setInt(14, s.getLastSeqCn());
			ps.setInt(15, s.getLastSeqDn());
			ps.setInt(16, s.getLastSeqQtn());
			ps.setInt(17, s.getLastSeqPo());
			ps.setInt(18, s.getLastSeqJob());
			ps.setInt(19, s.getLastSeqTkt());
			ps.setInt(20, s.getLastSeqDc());
			ps.setInt(21, s.getLastSeqEwb());

			ps.executeUpdate();
		}
	}
}
