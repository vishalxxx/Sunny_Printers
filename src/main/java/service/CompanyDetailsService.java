package service;

import model.CompanyDetails;
import repository.CompanyDetailsRepository;
import utils.CompanyProfile;
import utils.DBConnection;

import java.sql.Connection;
import java.util.List;

public class CompanyDetailsService {

	private final CompanyDetailsRepository repo = new CompanyDetailsRepository();

	public List<CompanyDetails> listActive() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.listAll(con, false);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load companies", e);
		}
	}

	public List<CompanyDetails> listAllIncludingInactive() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.listAll(con, true);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load companies", e);
		}
	}

	public CompanyDetails getDefault() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.findDefault(con);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load default company", e);
		}
	}

	public CompanyDetails save(CompanyDetails c) {
		if (c == null) throw new IllegalArgumentException("company");
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				if (c.isDefault()) {
					repo.clearDefault(con);
				}
				if (c.getUuid() == null || c.getUuid().isBlank()) {
					String uuid = repo.insert(con, c);
					c.setUuid(uuid);
				} else {
					repo.update(con, c);
				}
				enforceSingleDefault(con);
				con.commit();

				// Backward-compatible: keep Preferences in sync with the default company
				CompanyDetails def = repo.findDefault(con);
				if (def != null) {
					CompanyProfile.setName(def.getTradeName());
					CompanyProfile.setAddress(def.getAddress());
					CompanyProfile.setPhone(def.getPhone());
					CompanyProfile.setEmail(def.getEmail());
					CompanyProfile.setGst(def.getGstin());
				}
				return c;
			} catch (Exception e) {
				try { con.rollback(); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to rollback CompanyDetailsService.save: " + e2.getMessage()); }
				throw e;
			} finally {
				try { con.setAutoCommit(true); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to reset auto-commit in CompanyDetailsService.save: " + e2.getMessage()); }
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to save company", e);
		}
	}

	public void setDefaultCompany(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				repo.setDefault(con, uuid);
				con.commit();
			} catch (Exception e) {
				try { con.rollback(); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to rollback CompanyDetailsService.setDefaultCompany: " + e2.getMessage()); }
				throw e;
			} finally {
				try { con.setAutoCommit(true); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to reset auto-commit in setDefaultCompany: " + e2.getMessage()); }
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to set default company", e);
		}

		// sync Preferences with default company (for legacy invoice services)
		try {
			CompanyDetails def = getDefault();
			if (def != null) {
				CompanyProfile.setName(def.getTradeName());
				CompanyProfile.setAddress(def.getAddress());
				CompanyProfile.setPhone(def.getPhone());
				CompanyProfile.setEmail(def.getEmail());
				CompanyProfile.setGst(def.getGstin());
			}
		} catch (Exception e2) {
			service.LoggerService.dbWarn("Failed to sync Preferences with default company: " + e2.getMessage());
		}
	}

	public void delete(String uuid) {
		if (uuid == null || uuid.isBlank()) return;
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				repo.delete(con, uuid);
				enforceSingleDefault(con);
				con.commit();
			} catch (Exception e) {
				try { con.rollback(); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to rollback CompanyDetailsService.delete: " + e2.getMessage()); }
				throw e;
			} finally {
				try { con.setAutoCommit(true); } catch (Exception e2) { service.LoggerService.dbWarn("Failed to reset auto-commit in CompanyDetailsService.delete: " + e2.getMessage()); }
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete company", e);
		}
	}

	private void enforceSingleDefault(Connection con) throws Exception {
		CompanyDetails def = repo.findDefault(con);
		if (def == null) {
			List<CompanyDetails> active = repo.listAll(con, false);
			if (!active.isEmpty()) {
				repo.setDefault(con, active.get(0).getUuid());
			}
		}
	}
}
