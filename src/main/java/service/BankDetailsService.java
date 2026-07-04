package service;

import model.BankDetails;
import repository.BankDetailsRepository;
import utils.DBConnection;

import java.sql.Connection;
import java.util.List;

public class BankDetailsService {

	private final BankDetailsRepository repo = new BankDetailsRepository();

	public List<BankDetails> listActive() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.listAll(con, false);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load bank details", e);
		}
	}

	public List<BankDetails> listAllIncludingInactive() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.listAll(con, true);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load bank details", e);
		}
	}

	public BankDetails getDefault() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.findDefault(con);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load default bank details", e);
		}
	}

	public BankDetails save(BankDetails b) {
		if (b == null) {
			throw new IllegalArgumentException("bankDetails");
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				if (b.isDefault()) {
					repo.clearDefault(con);
				}
				if (b.getUuid() == null || b.getUuid().isBlank()) {
					String uuid = repo.insert(con, b);
					b.setUuid(uuid);
				} else {
					repo.update(con, b);
				}
				con.commit();
				return b;
			} catch (Exception e) {
				try {
					con.rollback();
				} catch (Exception e2) {
					service.LoggerService.dbWarn("Failed to rollback BankDetailsService.save: " + e2.getMessage());
				}
				throw e;
			} finally {
				try {
					con.setAutoCommit(true);
				} catch (Exception e2) {
					service.LoggerService.dbWarn("Failed to reset auto-commit in BankDetailsService.save: " + e2.getMessage());
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to save bank details", e);
		}
	}

	public void delete(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		try (Connection con = DBConnection.getConnection()) {
			repo.delete(con, uuid);
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete bank details", e);
		}
	}

	public void setDefaultBank(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				repo.setDefault(con, uuid);
				con.commit();
			} catch (Exception e) {
				try { con.rollback(); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to rollback BankDetailsService.setDefaultBank: " + e2.getMessage()); }
				throw e;
			} finally {
				try { con.setAutoCommit(true); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to reset auto-commit in setDefaultBank: " + e2.getMessage()); }
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to set default bank", e);
		}
	}
}
