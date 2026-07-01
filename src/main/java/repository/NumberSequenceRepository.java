package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import model.MasterDocumentSeries;
import model.NumberSequence;
import model.SystemSettings;
import utils.NumberSequenceCatalog;
import utils.NumberSequenceCatalog.ModuleDef;

public class NumberSequenceRepository {

	public List<NumberSequence> findAll(Connection con) throws Exception {
		List<NumberSequence> out = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement("""
				SELECT sequence_key, display_name, prefix,
				       current_number, digit_width, financial_year, offline_current_number
				FROM number_sequences
				ORDER BY sequence_key
				""");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				out.add(mapRow(rs));
			}
		}
		return out;
	}

	public void upsert(Connection con, NumberSequence row) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("""
				INSERT INTO number_sequences
				(sequence_key, display_name, prefix, current_number, digit_width, financial_year, sync_status, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 'SYNCED', datetime('now'))
				ON CONFLICT(sequence_key) DO UPDATE SET
				  display_name = excluded.display_name,
				  prefix = excluded.prefix,
				  current_number = excluded.current_number,
				  digit_width = excluded.digit_width,
				  financial_year = excluded.financial_year,
				  sync_status = 'SYNCED',
				  updated_at = datetime('now')
				""")) {
			ps.setString(1, row.getSequenceKey());
			ps.setString(2, row.getDisplayName());
			ps.setString(3, row.getPrefix());
			ps.setLong(4, row.getCurrentNumber());
			ps.setInt(5, row.getDigitWidth());
			ps.setString(6, row.getFinancialYear());
			ps.executeUpdate();
		}
	}

	public List<NumberSequence> syncFromSystemSettings(Connection con, SystemSettings settings, int digitWidth,
			String financialYear) throws Exception {
		ensureSeedRows(con, digitWidth, financialYear);
		int pad = Math.max(1, digitWidth);
		String fy = financialYear != null && !financialYear.isBlank() ? financialYear.trim() : "";

		for (MasterDocumentSeries series : MasterDocumentSeries.values()) {
			String key = NumberSequenceCatalog.moduleNameFor(series);
			NumberSequence row = rowForKey(con, key);
			if (row == null) {
				continue;
			}
			row.setCurrentNumber(Math.max(0, settings.getLastSeq(series)));
			row.setDigitWidth(pad);
			row.setFinancialYear(fy);
			upsert(con, row);
		}

		NumberSequence temp = rowForKey(con, "temp_invoice");
		if (temp != null) {
			temp.setCurrentNumber(Math.max(0, settings.getLastTempInvoiceNo()));
			temp.setDigitWidth(pad);
			temp.setFinancialYear(fy);
			upsert(con, temp);
		}

		for (NumberSequence row : findAll(con)) {
			if (NumberSequenceCatalog.seriesForModule(row.getSequenceKey()).isPresent()) {
				continue;
			}
			if ("temp_invoice".equals(row.getSequenceKey())) {
				continue;
			}
			row.setDigitWidth(pad);
			row.setFinancialYear(fy);
			upsert(con, row);
		}

		return findAll(con);
	}

	public NumberSequence findByKey(Connection con, String sequenceKey) throws Exception {
		return rowForKey(con, sequenceKey);
	}

	/** Advances local offline counter; used for TEMP-* when Supabase is unreachable. */
	public long nextOfflineNumber(Connection con, String sequenceKey) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("""
				UPDATE number_sequences
				SET offline_current_number = offline_current_number + 1,
				    updated_at = datetime('now')
				WHERE sequence_key = ?
				""")) {
			ps.setString(1, sequenceKey);
			if (ps.executeUpdate() == 0) {
				throw new IllegalStateException("Unknown sequence: " + sequenceKey);
			}
		}
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT offline_current_number FROM number_sequences WHERE sequence_key = ?")) {
			ps.setString(1, sequenceKey);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getLong(1);
				}
			}
		}
		throw new IllegalStateException("Unknown sequence: " + sequenceKey);
	}

	public void applyToSystemSettings(Connection con, SystemSettings settings) throws Exception {
		List<NumberSequence> rows = findAll(con);
		for (NumberSequence row : rows) {
			NumberSequenceCatalog.seriesForModule(row.getSequenceKey()).ifPresent(series -> settings
					.setLastSeq(series, (int) Math.min(Integer.MAX_VALUE, Math.max(0, row.getCurrentNumber()))));
			if ("temp_invoice".equals(row.getSequenceKey())) {
				settings.setLastTempInvoiceNo((int) Math.min(Integer.MAX_VALUE, Math.max(0, row.getCurrentNumber())));
			}
		}
		rows.stream().filter(r -> "gst_invoice".equals(r.getSequenceKey())).findFirst().ifPresent(gst -> {
			if (gst.getDigitWidth() > 0) {
				settings.setInvoicePadding(gst.getDigitWidth());
				settings.setJobPadding(gst.getDigitWidth());
			}
			if (gst.getFinancialYear() != null && !gst.getFinancialYear().isBlank()) {
				settings.setNumberingFy(gst.getFinancialYear().trim());
			}
		});
	}

	private void ensureSeedRows(Connection con, int digitWidth, String financialYear) throws Exception {
		int pad = Math.max(1, digitWidth);
		String fy = financialYear != null ? financialYear : "";
		for (ModuleDef def : NumberSequenceCatalog.ALL) {
			try (PreparedStatement ps = con.prepareStatement("""
					INSERT OR IGNORE INTO number_sequences
					(sequence_key, display_name, prefix, current_number, digit_width, financial_year, sync_status)
					VALUES (?, ?, ?, 0, ?, ?, 'SYNCED')
					""")) {
				ps.setString(1, def.moduleName());
				ps.setString(2, def.displayName());
				ps.setString(3, def.prefix());
				ps.setInt(4, pad);
				ps.setString(5, fy);
				ps.executeUpdate();
			}
		}
	}

	private NumberSequence rowForKey(Connection con, String sequenceKey) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("""
				SELECT sequence_key, display_name, prefix,
				       current_number, digit_width, financial_year, offline_current_number
				FROM number_sequences WHERE sequence_key = ?
				""")) {
			ps.setString(1, sequenceKey);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return mapRow(rs);
				}
			}
		}
		ModuleDef def = NumberSequenceCatalog.defForModule(sequenceKey).orElse(null);
		if (def == null) {
			return null;
		}
		NumberSequence row = new NumberSequence();
		row.setSequenceKey(def.moduleName());
		row.setDisplayName(def.displayName());
		row.setPrefix(def.prefix());
		row.setCurrentNumber(0);
		row.setDigitWidth(4);
		row.setFinancialYear("");
		return row;
	}

	private static NumberSequence mapRow(ResultSet rs) throws Exception {
		NumberSequence row = new NumberSequence();
		row.setSequenceKey(rs.getString("sequence_key"));
		row.setDisplayName(rs.getString("display_name"));
		row.setPrefix(rs.getString("prefix"));
		row.setCurrentNumber(rs.getLong("current_number"));
		row.setDigitWidth(rs.getInt("digit_width"));
		row.setFinancialYear(rs.getString("financial_year"));
		try {
			row.setOfflineCurrentNumber(rs.getLong("offline_current_number"));
		} catch (Exception ignored) {
			row.setOfflineCurrentNumber(0);
		}
		return row;
	}
}
