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
				if (b.getId() <= 0) {
					int id = repo.insert(con, b);
					b.setId(id);
				} else {
					repo.update(con, b);
				}
				con.commit();
				return b;
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
			throw new RuntimeException("Failed to save bank details", e);
		}
	}

	public void delete(int id) {
		if (id <= 0) {
			return;
		}
		try (Connection con = DBConnection.getConnection()) {
			repo.delete(con, id);
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete bank details", e);
		}
	}

	public void setDefaultBank(int bankId) {
		if (bankId <= 0) {
			return;
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				repo.setDefault(con, bankId);
				con.commit();
			} catch (Exception e) {
				try { con.rollback(); } catch (Exception ignored) {}
				throw e;
			} finally {
				try { con.setAutoCommit(true); } catch (Exception ignored) {}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to set default bank", e);
		}
	}
}

