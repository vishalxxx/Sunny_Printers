package service;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import model.TaxMasterItem;
import repository.TaxMasterRepository;
import utils.DBConnection;
import utils.DatabaseInitializer;

public class TaxMasterService {

	private final TaxMasterRepository repo = new TaxMasterRepository();

	public List<String> listCategories() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.distinctItemTypes(con);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load tax categories", e);
		}
	}

	public TaxMasterItem findByUuid(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return null;
		}
		try (Connection con = DBConnection.getConnection()) {
			return repo.findByUuid(con, uuid);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load tax item", e);
		}
	}

	public int countFiltered(String search, String categoryOrNull, String codeTypeOrNull, Integer activeFilter) {
		try (Connection con = DBConnection.getConnection()) {
			return repo.countFiltered(con, search, categoryOrNull, codeTypeOrNull, activeFilter);
		} catch (Exception e) {
			throw new RuntimeException("Failed to count tax items", e);
		}
	}

	public List<TaxMasterItem> listPage(String search, String categoryOrNull, String codeTypeOrNull,
			Integer activeFilter, int offset, int limit) {
		try (Connection con = DBConnection.getConnection()) {
			return repo.listFiltered(con, search, categoryOrNull, codeTypeOrNull, activeFilter, offset, limit);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load tax items", e);
		}
	}

	public TaxMasterItem save(TaxMasterItem row) {
		if (row == null) {
			throw new IllegalArgumentException("row");
		}
		requireHsnSac(row.getHsnSac());
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				if (row.getUuid() == null || row.getUuid().isBlank()) {
					String uuid = repo.insert(con, row);
					row.setUuid(uuid);
				} else {
					repo.update(con, row);
				}
				con.commit();
				return row;
			} catch (Exception e) {
				try {
					con.rollback();
				} catch (Exception ignored) {
				}
				throw e;
			} finally {
				try {
					con.setAutoCommit(true);
				} catch (Exception ignored) {
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to save tax item", e);
		}
	}

	public void setActive(String uuid, boolean active) {
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		try (Connection con = DBConnection.getConnection()) {
			repo.setActive(con, uuid, active);
		} catch (Exception e) {
			throw new RuntimeException("Failed to update tax item status", e);
		}
	}

	/** HSN/SAC required for GST compliance — reject blanks and placeholder tokens. */
	private static void requireHsnSac(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("HSN/SAC code is required.");
		}
		String t = raw.trim();
		if ("—".equals(t) || "-".equals(t) || ".".equals(t)) {
			throw new IllegalArgumentException("Enter a valid HSN/SAC code.");
		}
		if (t.equalsIgnoreCase("na") || t.equalsIgnoreCase("n/a") || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("nil")) {
			throw new IllegalArgumentException("Enter a valid HSN/SAC code.");
		}
	}

	/** Inserts baseline category rows via {@link DatabaseInitializer#seedDefaultHsnSacRows}. */
	public void importDefaults() {
		try (Connection con = DBConnection.getConnection();
				Statement st = con.createStatement()) {
			DatabaseInitializer.seedDefaultHsnSacRows(st);
		} catch (Exception e) {
			throw new RuntimeException("Failed to import default tax rows", e);
		}
	}
}
